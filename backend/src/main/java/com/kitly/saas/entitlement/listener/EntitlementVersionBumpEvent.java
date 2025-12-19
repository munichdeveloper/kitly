package com.kitly.saas.entitlement.listener;

import com.kitly.saas.entitlement.EntitlementService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

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
