package com.kitly.saas.service;

import com.kitly.saas.entity.Subscription;
import com.kitly.saas.common.exception.ResourceNotFoundException;
import com.kitly.saas.repository.SubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
public class EntitlementService {
    
    private final SubscriptionRepository subscriptionRepository;
    
    public EntitlementService(SubscriptionRepository subscriptionRepository) {
        this.subscriptionRepository = subscriptionRepository;
    }
    
    /**
     * Get plan catalog with features and pricing
     */
    public Map<String, PlanInfo> getPlanCatalog() {
        Map<String, PlanInfo> catalog = new HashMap<>();
        
        catalog.put("FREE", PlanInfo.builder()
                .name("Free")
                .maxSeats(3)
                .monthlyPrice(BigDecimal.ZERO)
                .yearlyPrice(BigDecimal.ZERO)
                .features(new String[]{"Basic features", "3 team members", "Community support"})
                .build());
        
        catalog.put("STARTER", PlanInfo.builder()
                .name("Starter")
                .maxSeats(10)
                .monthlyPrice(new BigDecimal("29.00"))
                .yearlyPrice(new BigDecimal("290.00"))
                .features(new String[]{"All Free features", "10 team members", "Email support", "Advanced analytics"})
                .build());
        
        catalog.put("PROFESSIONAL", PlanInfo.builder()
                .name("Professional")
                .maxSeats(50)
                .monthlyPrice(new BigDecimal("99.00"))
                .yearlyPrice(new BigDecimal("990.00"))
                .features(new String[]{"All Starter features", "50 team members", "Priority support", "Custom integrations"})
                .build());
        
        catalog.put("ENTERPRISE", PlanInfo.builder()
                .name("Enterprise")
                .maxSeats(null) // Unlimited
                .monthlyPrice(new BigDecimal("299.00"))
                .yearlyPrice(new BigDecimal("2990.00"))
                .features(new String[]{"All Professional features", "Unlimited team members", "24/7 support", "Dedicated account manager", "Custom SLA"})
                .build());
        
        return catalog;
    }
    
    /**
     * Get current entitlements for a tenant
     */
    public Entitlements getTenantEntitlements(UUID tenantId) {
        // Find subscription with ACTIVE or TRIALING status
        Subscription subscription = subscriptionRepository.findByTenantIdAndStatus(
                tenantId, 
                Subscription.SubscriptionStatus.ACTIVE
        ).or(() -> subscriptionRepository.findByTenantIdAndStatus(
                tenantId,
                Subscription.SubscriptionStatus.TRIALING
        )).orElseThrow(() -> new ResourceNotFoundException("No active or trialing subscription found"));
        
        return Entitlements.builder()
                .tenantId(tenantId)
                .plan(subscription.getPlan().name())
                .maxSeats(subscription.getMaxSeats())
                .version(subscription.getEntitlementVersion())
                .build();
    }
    
    /**
     * Bump entitlement version for optimistic locking
     */
    @Transactional
    public void bumpEntitlementVersion(UUID tenantId) {
        // Find subscription with ACTIVE or TRIALING status
        Subscription subscription = subscriptionRepository.findByTenantIdAndStatus(
                tenantId, 
                Subscription.SubscriptionStatus.ACTIVE
        ).or(() -> subscriptionRepository.findByTenantIdAndStatus(
                tenantId,
                Subscription.SubscriptionStatus.TRIALING
        )).orElseThrow(() -> new ResourceNotFoundException("No active or trialing subscription found"));
        
        // Save will trigger @Version increment automatically
        subscriptionRepository.save(subscription);
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PlanInfo {
        private String name;
        private Integer maxSeats;
        private BigDecimal monthlyPrice;
        private BigDecimal yearlyPrice;
        private String[] features;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class Entitlements {
        private UUID tenantId;
        private String plan;
        private Integer maxSeats;
        private Long version;
    }
}
