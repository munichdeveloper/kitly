package com.kitly.saas.service;

import com.kitly.saas.dto.AuthResponse;
import com.kitly.saas.entity.Membership;
import com.kitly.saas.entity.User;
import com.kitly.saas.exception.UnauthorizedException;
import com.kitly.saas.repository.MembershipRepository;
import com.kitly.saas.repository.UserRepository;
import com.kitly.saas.security.JwtUtil;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for tenant-scoped authentication.
 * Generates JWT tokens that include tenant context and tenant-specific roles.
 */
@Service
public class TenantAuthService {
    
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final JwtUtil jwtUtil;
    
    public TenantAuthService(UserRepository userRepository,
                            MembershipRepository membershipRepository,
                            JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.jwtUtil = jwtUtil;
    }
    
    /**
     * Generate a tenant-scoped JWT token for a user.
     * The token includes the tenant ID and combines global roles with tenant-specific membership roles.
     */
    public AuthResponse generateTenantToken(String username, UUID tenantId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        
        // Verify user is a member of the tenant
        Membership membership = membershipRepository.findByTenantIdAndUserId(tenantId, user.getId())
                .orElseThrow(() -> new UnauthorizedException("User is not a member of this tenant"));
        
        // Check membership is active
        if (membership.getStatus() != Membership.MembershipStatus.ACTIVE) {
            throw new UnauthorizedException("Membership is not active");
        }
        
        // Combine global roles with tenant-specific roles
        Set<GrantedAuthority> authorities = new HashSet<>();
        
        // Add global roles
        authorities.addAll(user.getRoles().stream()
                .map(role -> new SimpleGrantedAuthority(role.getName().name()))
                .collect(Collectors.toSet()));
        
        // Add tenant-specific role as TENANT_OWNER, TENANT_ADMIN, or TENANT_MEMBER
        authorities.add(new SimpleGrantedAuthority("TENANT_" + membership.getRole().name()));
        
        // Create UserDetails with combined authorities
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .authorities(authorities)
                .build();
        
        // Generate token with tenant ID
        String token = jwtUtil.generateToken(userDetails, tenantId);
        
        return new AuthResponse(token, user.getUsername(), user.getEmail());
    }
    
    /**
     * Get the membership role for a user in a tenant
     */
    public String getTenantRole(String username, UUID tenantId) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new UnauthorizedException("User not found"));
        
        Membership membership = membershipRepository.findByTenantIdAndUserId(tenantId, user.getId())
                .orElseThrow(() -> new UnauthorizedException("User is not a member of this tenant"));
        
        return membership.getRole().name();
    }
}
