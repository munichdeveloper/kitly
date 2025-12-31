package de.atstck.kitly.entitlement.listener;

import java.util.UUID;

/**
 * Event to signal that entitlement version should be bumped for a tenant
 */
public class EntitlementVersionBumpEvent {
    private final UUID tenantId;
    
    public EntitlementVersionBumpEvent(UUID tenantId) {
        this.tenantId = tenantId;
    }
    
    public UUID getTenantId() {
        return tenantId;
    }
}
