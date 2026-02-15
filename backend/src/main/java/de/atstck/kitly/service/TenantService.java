package de.atstck.kitly.service;

import de.atstck.kitly.common.context.TenantContextHolder;
import de.atstck.kitly.dto.TenantRequest;
import de.atstck.kitly.dto.TenantResponse;
import de.atstck.kitly.entitlement.PlanService;
import de.atstck.kitly.entity.*;
import de.atstck.kitly.common.exception.BadRequestException;
import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.common.exception.UnauthorizedException;
import de.atstck.kitly.repository.MembershipRepository;
import de.atstck.kitly.repository.SubscriptionRepository;
import de.atstck.kitly.repository.TenantRepository;
import de.atstck.kitly.repository.UserRepository;
import de.atstck.kitly.repository.EntitlementVersionRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class TenantService {

    private final TenantRepository tenantRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementVersionRepository entitlementVersionRepository;
    private final PlanService planService;

    public TenantService(TenantRepository tenantRepository,
                         UserRepository userRepository,
                         MembershipRepository membershipRepository,
                         SubscriptionRepository subscriptionRepository,
                         EntitlementVersionRepository entitlementVersionRepository,
                         PlanService planService) {
        this.tenantRepository = tenantRepository;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.entitlementVersionRepository = entitlementVersionRepository;
        this.planService = planService;
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

        // Link the owner user with the tenant (if not already linked)
        // This ensures user.tenant is set to the created tenant
        if (owner.getTenant() == null) {
            owner.setTenant(tenant);
            userRepository.save(owner);
            log.debug("Linked owner user {} to tenant {}", username, tenant.getId());
        }

        // Create OWNER membership
        Membership membership = Membership.builder()
                .tenant(tenant)
                .user(owner)
                .role(Membership.MembershipRole.OWNER)
                .status(Membership.MembershipStatus.ACTIVE)
                .build();

        membershipRepository.save(membership);

        // ...rest of the method...

        // Get the FREE plan (or default plan)
        PlanEntity freePlan;
        try {
            log.debug("Attempting to load FREE plan for tenant: {}", request.getSlug());
            freePlan = planService.getPlan("FREE");
            log.debug("Successfully loaded FREE plan with ID: {}", freePlan.getId());
        } catch (Exception e) {
            log.warn("Failed to load FREE plan: {}. Attempting fallback to STARTER plan.", e.getMessage());
            // Try to get any active plan as fallback
            try {
                log.debug("Attempting to load STARTER plan as fallback");
                freePlan = planService.getPlan("STARTER");
                log.warn("Using STARTER plan as fallback instead of FREE plan for tenant: {}", request.getSlug());
            } catch (Exception e2) {
                log.error("Failed to load both FREE and STARTER plans. Tenant creation cannot proceed. " +
                                "Please ensure at least one of these plans exists in the database. " +
                                "FREE plan error: {} | STARTER plan error: {}",
                        e.getMessage(), e2.getMessage(), e2);
                throw new IllegalStateException(
                        "No subscription plan found (FREE or STARTER). " +
                                "Please create at least one of these plans in the database first. " +
                                "Error: " + e.getMessage(), e);
            }
        }

        // Create default subscription with TRIALING status
        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(freePlan)
                .status(Subscription.SubscriptionStatus.TRIALING)
                .startsAt(LocalDateTime.now())
                .trialEndsAt(LocalDateTime.now().plusDays(14))
                .currency("USD")
                .build();

        subscriptionRepository.save(subscription);

        // Create initial entitlement version to avoid race conditions
        EntitlementVersion entitlementVersion = EntitlementVersion.builder()
                .tenant(tenant)
                .version(1L)
                .build();

        entitlementVersionRepository.save(entitlementVersion);

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
