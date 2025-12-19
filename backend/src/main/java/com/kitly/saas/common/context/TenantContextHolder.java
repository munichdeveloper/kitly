package com.kitly.saas.common.context;

import java.util.UUID;

/**
 * Thread-local storage for tenant context information.
 * Used to maintain tenant isolation in multi-tenant operations.
 */
public class TenantContextHolder {
    
    private static final ThreadLocal<TenantContext> contextHolder = new ThreadLocal<>();
    
    private TenantContextHolder() {
        // Private constructor to prevent instantiation
    }
    
    public static void setContext(TenantContext context) {
        contextHolder.set(context);
    }
    
    public static TenantContext getContext() {
        TenantContext context = contextHolder.get();
        if (context == null) {
            context = new TenantContext();
            contextHolder.set(context);
        }
        return context;
    }
    
    public static UUID getTenantId() {
        TenantContext context = getContext();
        return context != null ? context.getTenantId() : null;
    }
    
    public static void setTenantId(UUID tenantId) {
        TenantContext context = getContext();
        context.setTenantId(tenantId);
    }
    
    public static void clear() {
        contextHolder.remove();
    }
}
