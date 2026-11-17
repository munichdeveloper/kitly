package de.atstck.kitly.entitlement;

import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.entity.*;
import de.atstck.kitly.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
    private final PlanService planService;

    public EntitlementService(
            SubscriptionRepository subscriptionRepository,
            EntitlementRepository entitlementRepository,
            EntitlementVersionRepository entitlementVersionRepository,
            MembershipRepository membershipRepository,
            TenantRepository tenantRepository,
            PlanService planService) {
        this.subscriptionRepository = subscriptionRepository;
        this.entitlementRepository = entitlementRepository;
        this.entitlementVersionRepository = entitlementVersionRepository;
        this.membershipRepository = membershipRepository;
        this.tenantRepository = tenantRepository;
        this.planService = planService;
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
        
        List<Subscription> subscriptions = subscriptionRepository.findAccessSubscriptions(tenantId);
        Set<String> activePlans = new LinkedHashSet<>();
        for (Subscription subscription : subscriptions) {
            String code = subscription.getPlanCode();
            if (tenant.getPlanAccess().getOrDefault(code, true)) {
                activePlans.add(code);
            }
        }
        tenant.getPlanAccess().entrySet().stream().filter(Map.Entry::getValue)
                .map(Map.Entry::getKey).sorted().forEach(activePlans::add);
        if (activePlans.isEmpty()) {
            throw new ResourceNotFoundException("No active plan found");
        }

        Map<String, EntitlementResponse.EntitlementItem> mergedEntitlements = new LinkedHashMap<>();
        Subscription primary = subscriptions.isEmpty() ? null : subscriptions.get(0);
        for (String code : activePlans) {
            planService.getPlanEntitlements(code).forEach((key, value) -> {
                var item = EntitlementResponse.EntitlementItem.builder().key(key).value(value).source("PLAN").build();
                mergedEntitlements.merge(key, item, EntitlementService::morePermissive);
            });

            // Legacy rows include cached subscription rights. Apply them only with their base plan.
            if (primary != null && code.equals(primary.getPlanCode())) {
                for (Entitlement override : entitlementRepository.findByTenantAndEnabled(tenant, true)) {
                    String key = override.getFeatureKey();
                    mergedEntitlements.put(key, EntitlementResponse.EntitlementItem.builder()
                            .key(key).value(getEntitlementValue(override)).source("OVERRIDE").build());
                }
            }
        }

        // Get active seats count
        long activeSeats = membershipRepository.countByTenantIdAndStatus(
                tenantId, Membership.MembershipStatus.ACTIVE);
        
        // Get or create entitlement version (with retry on duplicate)
        EntitlementVersion version = getOrCreateEntitlementVersion(tenant);

        // Build response
        return EntitlementResponse.builder()
                .tenantId(tenantId)
                .planCode(activePlans.iterator().next())
                .activePlanCodes(List.copyOf(activePlans))
                .status(primary != null && activePlans.contains(primary.getPlanCode()) ? primary.getStatus().name() : "ACTIVE")
                .seatsQuantity(primary == null ? null : primary.getMaxSeats())
                .activeSeats(activeSeats)
                .entitlementVersion(version.getVersion())
                .items(new ArrayList<>(mergedEntitlements.values()))
                .build();
    }
    
    private static EntitlementResponse.EntitlementItem morePermissive(
            EntitlementResponse.EntitlementItem current, EntitlementResponse.EntitlementItem candidate) {
        String value = candidate.getValue();
        if ("unlimited".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value)) {
            return candidate;
        }
        try {
            return Long.parseLong(value) > Long.parseLong(current.getValue()) ? candidate : current;
        } catch (NumberFormatException ignored) {
            return current;
        }
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
     * Sync entitlements from the current subscription plan to the database.
     * This ensures the entitlements table reflects the current plan's features.
     */
    @Transactional
    public void syncEntitlements(UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));

        Subscription subscription = getActiveSubscription(tenantId);

        // Get plan code from subscription's PlanEntity
        String planCode = subscription.getPlanCode();
        if (planCode == null) {
            return; // No plan assigned
        }

        // Get plan entitlements from PlanService (database)
        Map<String, String> planEntitlements;
        try {
            planEntitlements = planService.getPlanEntitlements(planCode);
        } catch (ResourceNotFoundException e) {
            // Plan not found, nothing to sync
            return;
        }

        // Get existing entitlements
        List<Entitlement> existingEntitlements = entitlementRepository.findByTenant(tenant);
        Map<String, Entitlement> existingMap = existingEntitlements.stream()
                .collect(Collectors.toMap(Entitlement::getFeatureKey, e -> e));

        // Sync plan entitlements
        for (Map.Entry<String, String> entry : planEntitlements.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();

            Entitlement entitlement = existingMap.get(key);
            if (entitlement == null) {
                entitlement = Entitlement.builder()
                        .tenant(tenant)
                        .featureKey(key)
                        .featureType(inferFeatureType(value))
                        .build();
            }

            updateEntitlementValue(entitlement, value);
            entitlementRepository.save(entitlement);
        }

        bumpEntitlementVersion(tenantId);
    }

    private Entitlement.FeatureType inferFeatureType(String value) {
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Entitlement.FeatureType.BOOLEAN;
        }
        return Entitlement.FeatureType.LIMIT;
    }

    private void updateEntitlementValue(Entitlement entitlement, String value) {
        if (entitlement.getFeatureType() == Entitlement.FeatureType.BOOLEAN) {
            entitlement.setEnabled(Boolean.parseBoolean(value));
        } else {
            if ("unlimited".equalsIgnoreCase(value)) {
                entitlement.setLimitValue(-1L);
            } else {
                try {
                    entitlement.setLimitValue(Long.parseLong(value));
                } catch (NumberFormatException e) {
                    entitlement.setLimitValue(0L);
                }
            }
            entitlement.setEnabled(true);
        }
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
        return subscriptionRepository.findAccessSubscriptions(tenantId).stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found"));
    }
    
    /**
     * Get string value from Entitlement entity
     */
    private String getEntitlementValue(Entitlement entitlement) {
        return switch (entitlement.getFeatureType()) {
            case BOOLEAN -> entitlement.getEnabled() ? "true" : "false";
            case LIMIT, QUOTA -> {
                if (entitlement.getLimitValue() != null && entitlement.getLimitValue() == -1) {
                    yield "unlimited";
                }
                yield entitlement.getLimitValue() != null
                        ? entitlement.getLimitValue().toString()
                        : "0";
            }
        };
    }
}
