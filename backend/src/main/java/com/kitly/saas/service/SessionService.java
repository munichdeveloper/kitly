package com.kitly.saas.service;

import com.kitly.saas.common.exception.BadRequestException;
import com.kitly.saas.common.exception.ResourceNotFoundException;
import com.kitly.saas.common.exception.UnauthorizedException;
import com.kitly.saas.dto.CurrentSessionResponse;
import com.kitly.saas.dto.RefreshTokenResponse;
import com.kitly.saas.dto.SessionResponse;
import com.kitly.saas.dto.SwitchTenantRequest;
import com.kitly.saas.entity.EntitlementVersion;
import com.kitly.saas.entity.Membership;
import com.kitly.saas.entity.Tenant;
import com.kitly.saas.entity.User;
import com.kitly.saas.repository.EntitlementVersionRepository;
import com.kitly.saas.repository.MembershipRepository;
import com.kitly.saas.repository.TenantRepository;
import com.kitly.saas.repository.UserRepository;
import com.kitly.saas.security.JwtUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SessionService {
    
    private final MembershipRepository membershipRepository;
    private final UserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final EntitlementVersionRepository entitlementVersionRepository;
    private final JwtUtil jwtUtil;
    
    @Value("${jwt.session.expiration}")
    private Long sessionExpiration;
    
    public SessionService(MembershipRepository membershipRepository,
                         UserRepository userRepository,
                         TenantRepository tenantRepository,
                         EntitlementVersionRepository entitlementVersionRepository,
                         JwtUtil jwtUtil) {
        this.membershipRepository = membershipRepository;
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.entitlementVersionRepository = entitlementVersionRepository;
        this.jwtUtil = jwtUtil;
    }
    
    /**
     * Switch the user's active tenant session.
     * Validates that the user has an active membership in the target tenant.
     *
     * @param request Switch tenant request containing tenantId
     * @param username Current authenticated username
     * @return SessionResponse with new tenant-scoped JWT
     */
    public SessionResponse switchTenant(SwitchTenantRequest request, String username) {
        // Get user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        UUID tenantId = request.getTenantId();
        
        // Validate membership exists
        Membership membership = membershipRepository.findByTenantIdAndUserId(tenantId, user.getId())
                .orElseThrow(() -> new BadRequestException("User is not a member of the specified tenant"));
        
        // Validate membership is active
        if (membership.getStatus() != Membership.MembershipStatus.ACTIVE) {
            throw new UnauthorizedException("Membership is not active. Status: " + membership.getStatus());
        }
        
        // Get tenant
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        
        // Validate tenant is active
        if (tenant.getStatus() != Tenant.TenantStatus.ACTIVE) {
            throw new UnauthorizedException("Tenant is not active. Status: " + tenant.getStatus());
        }
        
        // Get entitlement version
        Long entitlementVersion = getEntitlementVersion(tenant);
        
        // Extract tenant role
        List<String> roles = List.of(membership.getRole().name());
        
        // Generate tenant-scoped token
        String token = jwtUtil.generateTenantToken(user.getId(), tenantId, roles, entitlementVersion);
        
        return SessionResponse.builder()
                .token(token)
                .type("Bearer")
                .userId(user.getId())
                .tenantId(tenantId)
                .roles(roles)
                .entitlementVersion(entitlementVersion)
                .expiresIn(sessionExpiration)
                .build();
    }
    
    /**
     * Refresh the current session token, extending its validity.
     * Validates the current token and generates a new one with the same claims.
     *
     * @param token Current JWT token
     * @param username Current authenticated username
     * @return RefreshTokenResponse with new token
     */
    public RefreshTokenResponse refreshSession(String token, String username) {
        // Extract claims from current token
        UUID tenantId = jwtUtil.extractTenantId(token);
        if (tenantId == null) {
            throw new BadRequestException("Token does not contain tenant context");
        }
        
        List<String> roles = jwtUtil.extractRoles(token);
        Long entitlementVersion = jwtUtil.extractEntitlementVersion(token);
        
        // Get user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Validate membership still exists and is active
        Membership membership = membershipRepository.findByTenantIdAndUserId(tenantId, user.getId())
                .orElseThrow(() -> new UnauthorizedException("Membership no longer exists"));
        
        if (membership.getStatus() != Membership.MembershipStatus.ACTIVE) {
            throw new UnauthorizedException("Membership is no longer active");
        }
        
        // Get latest entitlement version
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        Long latestEntitlementVersion = getEntitlementVersion(tenant);
        
        // Generate new token with updated entitlement version
        String newToken = jwtUtil.generateTenantToken(user.getId(), tenantId, roles, latestEntitlementVersion);
        
        return RefreshTokenResponse.builder()
                .token(newToken)
                .type("Bearer")
                .expiresIn(sessionExpiration)
                .build();
    }
    
    /**
     * Get current session information.
     *
     * @param token Current JWT token
     * @param username Current authenticated username
     * @return CurrentSessionResponse with session details
     */
    public CurrentSessionResponse getCurrentSession(String token, String username) {
        // Extract claims from token
        UUID tenantId = jwtUtil.extractTenantId(token);
        List<String> roles = jwtUtil.extractRoles(token);
        Long entitlementVersion = jwtUtil.extractEntitlementVersion(token);
        
        // Get user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        String tenantName = null;
        if (tenantId != null) {
            Tenant tenant = tenantRepository.findById(tenantId).orElse(null);
            if (tenant != null) {
                tenantName = tenant.getName();
            }
        }
        
        return CurrentSessionResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .tenantId(tenantId)
                .tenantName(tenantName)
                .roles(roles)
                .entitlementVersion(entitlementVersion)
                .build();
    }
    
    /**
     * Get the current entitlement version for a tenant.
     *
     * @param tenant Tenant entity
     * @return Current entitlement version, or 1L if not found
     */
    private Long getEntitlementVersion(Tenant tenant) {
        return entitlementVersionRepository.findByTenant(tenant)
                .map(EntitlementVersion::getVersion)
                .orElse(1L);
    }
}
