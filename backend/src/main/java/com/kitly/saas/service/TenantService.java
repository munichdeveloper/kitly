package com.kitly.saas.service;

import com.kitly.saas.context.TenantContextHolder;
import com.kitly.saas.dto.TenantRequest;
import com.kitly.saas.dto.TenantResponse;
import com.kitly.saas.entity.Membership;
import com.kitly.saas.entity.Subscription;
import com.kitly.saas.entity.Tenant;
import com.kitly.saas.entity.User;
import com.kitly.saas.exception.BadRequestException;
import com.kitly.saas.exception.ResourceNotFoundException;
import com.kitly.saas.exception.UnauthorizedException;
import com.kitly.saas.repository.MembershipRepository;
import com.kitly.saas.repository.SubscriptionRepository;
import com.kitly.saas.repository.TenantRepository;
import com.kitly.saas.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class TenantService {
    
    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final SubscriptionRepository subscriptionRepository;
    
    public TenantService(TenantRepository tenantRepository, 
                        UserRepository userRepository,
                        MembershipRepository membershipRepository,
                        SubscriptionRepository subscriptionRepository) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.subscriptionRepository = subscriptionRepository;
    }
    
    @Transactional
    public TenantResponse createTenant(TenantRequest request, String username) {
        // Validate slug uniqueness
        if (tenantRepository.existsBySlug(request.getSlug())) {
            throw new BadRequestException("Tenant slug already exists");
        }
        
        // Get current user
        User owner = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Create tenant
        Tenant tenant = Tenant.builder()
                .name(request.getName())
                .slug(request.getSlug())
                .domain(request.getDomain())
                .status(Tenant.TenantStatus.ACTIVE)
                .owner(owner)
                .build();
        
        tenant = tenantRepository.save(tenant);
        
        // Create OWNER membership
        Membership membership = Membership.builder()
                .tenant(tenant)
                .user(owner)
                .role(Membership.MembershipRole.OWNER)
                .status(Membership.MembershipStatus.ACTIVE)
                .build();
        
        membershipRepository.save(membership);
        
        // Create default subscription with TRIALING status
        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(Subscription.SubscriptionPlan.FREE)
                .status(Subscription.SubscriptionStatus.TRIALING)
                .startsAt(LocalDateTime.now())
                .trialEndsAt(LocalDateTime.now().plusDays(14))
                .currency("USD")
                .build();
        
        subscriptionRepository.save(subscription);
        
        return mapToTenantResponse(tenant);
    }
    
    public TenantResponse getTenantById(UUID tenantId) {
        validateTenantAccess(tenantId);
        
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        
        return mapToTenantResponse(tenant);
    }
    
    public List<TenantResponse> getUserTenants(String username) {
        // Get user
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Get all memberships for the user
        List<Membership> memberships = membershipRepository.findByUserId(user.getId());
        
        // Map to tenant responses
        return memberships.stream()
                .filter(m -> m.getStatus() == Membership.MembershipStatus.ACTIVE)
                .map(m -> mapToTenantResponse(m.getTenant()))
                .collect(Collectors.toList());
    }
    
    private void validateTenantAccess(UUID tenantId) {
        UUID contextTenantId = TenantContextHolder.getTenantId();
        if (contextTenantId != null && !contextTenantId.equals(tenantId)) {
            throw new UnauthorizedException("Access denied to this tenant");
        }
    }
    
    private TenantResponse mapToTenantResponse(Tenant tenant) {
        return TenantResponse.builder()
                .id(tenant.getId())
                .name(tenant.getName())
                .slug(tenant.getSlug())
                .domain(tenant.getDomain())
                .status(tenant.getStatus().name())
                .ownerId(tenant.getOwner() != null ? tenant.getOwner().getId() : null)
                .createdAt(tenant.getCreatedAt())
                .updatedAt(tenant.getUpdatedAt())
                .build();
    }
}
