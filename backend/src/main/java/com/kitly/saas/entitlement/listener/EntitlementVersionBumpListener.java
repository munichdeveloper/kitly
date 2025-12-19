package com.kitly.saas.entitlement.listener;

import com.kitly.saas.entitlement.EntitlementService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listener that handles entitlement version bump events
 */
@Component
public class EntitlementVersionBumpListener {
    
    private final EntitlementService entitlementService;
    
    public EntitlementVersionBumpListener(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }
    
    @EventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleVersionBump(EntitlementVersionBumpEvent event) {
        try {
            entitlementService.bumpEntitlementVersion(event.getTenantId());
        } catch (Exception e) {
            // Log error but don't fail the transaction
            // In production, consider using a retry mechanism
            System.err.println("Failed to bump entitlement version for tenant " + 
                    event.getTenantId() + ": " + e.getMessage());
        }
    }
}
