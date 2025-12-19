package com.kitly.saas.entitlement.listener;

import com.kitly.saas.entity.Membership;
import com.kitly.saas.entitlement.EntitlementService;
import jakarta.persistence.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * JPA entity listener to bump entitlement version when memberships change
 */
@Component
public class MembershipEntityListener {
    
    private static EntitlementService entitlementService;
    
    @Autowired
    public void setEntitlementService(EntitlementService entitlementService) {
        MembershipEntityListener.entitlementService = entitlementService;
    }
    
    @PostPersist
    public void afterCreate(Membership membership) {
        if (entitlementService != null && membership.getTenant() != null) {
            entitlementService.bumpEntitlementVersion(membership.getTenant().getId());
        }
    }
    
    @PostUpdate
    public void afterUpdate(Membership membership) {
        if (entitlementService != null && membership.getTenant() != null) {
            entitlementService.bumpEntitlementVersion(membership.getTenant().getId());
        }
    }
    
    @PostRemove
    public void afterDelete(Membership membership) {
        if (entitlementService != null && membership.getTenant() != null) {
            entitlementService.bumpEntitlementVersion(membership.getTenant().getId());
        }
    }
}
