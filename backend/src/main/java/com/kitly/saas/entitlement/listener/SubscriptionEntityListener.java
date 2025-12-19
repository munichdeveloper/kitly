package com.kitly.saas.entitlement.listener;

import com.kitly.saas.entity.Subscription;
import com.kitly.saas.entitlement.EntitlementService;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA entity listener to bump entitlement version when subscriptions change
 */
@Component
public class SubscriptionEntityListener {
    
    private static EntitlementService entitlementService;
    
    @Autowired
    public void setEntitlementService(EntitlementService entitlementService) {
        SubscriptionEntityListener.entitlementService = entitlementService;
    }
    
    @PostUpdate
    public void afterUpdate(Subscription subscription) {
        if (entitlementService != null && subscription.getTenant() != null) {
            entitlementService.bumpEntitlementVersion(subscription.getTenant().getId());
        }
    }
    
    @PostPersist
    public void afterCreate(Subscription subscription) {
        if (entitlementService != null && subscription.getTenant() != null) {
            entitlementService.bumpEntitlementVersion(subscription.getTenant().getId());
        }
    }
}
