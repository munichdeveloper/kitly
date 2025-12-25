package com.kitly.saas.entitlement;

import com.kitly.saas.common.exception.ResourceNotFoundException;
import com.kitly.saas.entity.*;
import com.kitly.saas.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Service for computing and managing tenant entitlements.
 * Entitlements are computed by merging PLAN → ADDON → OVERRIDE (in priority order).
 */
@Service
public class EntitlementService {
    
    private final SubscriptionRepository subscriptionRepository;
    private final EntitlementRepository entitlementRepository;
    private final EntitlementVersionRepository entitlementVersionRepository;
    private final MembershipRepository membershipRepository;
    private final TenantRepository tenantRepository;
    
    public EntitlementService(
            SubscriptionRepository subscriptionRepository,
            EntitlementRepository entitlementRepository,
            EntitlementVersionRepository entitlementVersionRepository,
            MembershipRepository membershipRepository,
            TenantRepository tenantRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.entitlementRepository = entitlementRepository;
        this.entitlementVersionRepository = entitlementVersionRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
    }
    
    /**
     * Compute entitlements for a tenant by merging PLAN → ADDON → OVERRIDE
     * and persist to entitlements table for caching
     */
    @Transactional
    public EntitlementResponse computeEntitlements(UUID tenantId) {
        // Get tenant
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        
        // Get active subscription
        Subscription subscription = getActiveSubscription(tenantId);
        
        // Get plan entitlements
        String planCode = mapSubscriptionPlanToPlanCode(subscription.getPlan());
        PlanCatalog.PlanDefinition plan = PlanCatalog.getPlan(planCode);
        if (plan == null) {
            throw new IllegalStateException("Invalid plan code: " + planCode);
        }
        
        // Start with plan entitlements
        Map<String, EntitlementResponse.EntitlementItem> mergedEntitlements = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : plan.getEntitlements().entrySet()) {
            mergedEntitlements.put(entry.getKey(), EntitlementResponse.EntitlementItem.builder()
                    .key(entry.getKey())
                    .value(entry.getValue())
                    .source("PLAN")
                    .build());
        }
        
        // Apply overrides from entitlements table
        List<Entitlement> overrides = entitlementRepository.findByTenantAndEnabled(tenant, true);
        for (Entitlement override : overrides) {
            String value = getEntitlementValue(override);
            mergedEntitlements.put(override.getFeatureKey(), EntitlementResponse.EntitlementItem.builder()
                    .key(override.getFeatureKey())
                    .value(value)
                    .source("OVERRIDE")
                    .build());
        }
        
        // Get active seats count
        long activeSeats = membershipRepository.countByTenantIdAndStatus(
                tenantId, Membership.MembershipStatus.ACTIVE);
        
        // Get or create entitlement version (with retry on duplicate)
        EntitlementVersion version = getOrCreateEntitlementVersion(tenant);

        // Build response
        return EntitlementResponse.builder()
                .tenantId(tenantId)
                .planCode(planCode)
                .status(subscription.getStatus().name())
                .seatsQuantity(subscription.getMaxSeats())
                .activeSeats(activeSeats)
                .entitlementVersion(version.getVersion())
                .items(new ArrayList<>(mergedEntitlements.values()))
                .build();
    }
    
    /**
     * Bump entitlement version for cache invalidation
     */
    @Transactional
    public void bumpEntitlementVersion(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        
        EntitlementVersion version = getOrCreateEntitlementVersion(tenant);
        version.setVersion(version.getVersion() + 1);
        entitlementVersionRepository.save(version);
    }

    /**
     * Get or create EntitlementVersion with retry logic to handle race conditions
     */
    private EntitlementVersion getOrCreateEntitlementVersion(Tenant tenant) {
        // Try to find existing version first
        Optional<EntitlementVersion> existingVersion = entitlementVersionRepository.findByTenant(tenant);
        if (existingVersion.isPresent()) {
            return existingVersion.get();
        }

        // If not found, try to create - this may fail if another thread created it
        try {
            EntitlementVersion newVersion = EntitlementVersion.builder()
                    .tenant(tenant)
                    .version(1L)
                    .build();
            return entitlementVersionRepository.save(newVersion);
        } catch (Exception e) {
            // If save failed due to duplicate, try to fetch again
            // Another thread likely created it between our check and insert
            return entitlementVersionRepository.findByTenant(tenant)
                    .orElseThrow(() -> new IllegalStateException(
                            "Failed to get or create EntitlementVersion for tenant: " + tenant.getId(), e));
        }
    }
    
    /**
     * Get active subscription for a tenant
     */
    private Subscription getActiveSubscription(UUID tenantId) {
        return subscriptionRepository.findByTenantIdAndStatus(
                tenantId, Subscription.SubscriptionStatus.ACTIVE)
                .or(() -> subscriptionRepository.findByTenantIdAndStatus(
                        tenantId, Subscription.SubscriptionStatus.TRIALING))
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found"));
    }
    
    /**
     * Map Subscription.SubscriptionPlan enum to plan catalog code
     * Note: FREE plan maps to 'starter' as it uses the same entitlements
     */
    private String mapSubscriptionPlanToPlanCode(Subscription.SubscriptionPlan plan) {
        return switch (plan) {
            case STARTER -> "starter";
            case BUSINESS -> "business";
            case ENTERPRISE -> "enterprise";
            case FREE -> "starter"; // FREE uses starter entitlements
        };
    }
    
    /**
     * Get string value from Entitlement entity
     */
    private String getEntitlementValue(Entitlement entitlement) {
        return switch (entitlement.getFeatureType()) {
            case BOOLEAN -> entitlement.getEnabled() ? "true" : "false";
            case LIMIT, QUOTA -> entitlement.getLimitValue() != null 
                    ? entitlement.getLimitValue().toString() 
                    : "0";
        };
    }
}
