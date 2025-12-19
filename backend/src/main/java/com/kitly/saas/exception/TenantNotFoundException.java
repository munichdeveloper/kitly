package com.kitly.saas.exception;

import java.util.UUID;

public class TenantNotFoundException extends ResourceNotFoundException {
    
    public TenantNotFoundException(UUID tenantId) {
        super("Tenant", "id", tenantId);
    }
    
    public TenantNotFoundException(String slug) {
        super("Tenant", "slug", slug);
    }
}
