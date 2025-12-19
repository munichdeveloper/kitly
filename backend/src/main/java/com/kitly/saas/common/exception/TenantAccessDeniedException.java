package com.kitly.saas.common.exception;

/**
 * Exception thrown when a user attempts to access a tenant they don't have access to.
 * This occurs when the tenant ID in the request path doesn't match the tenant ID in the JWT token.
 */
public class TenantAccessDeniedException extends RuntimeException {
    
    public TenantAccessDeniedException(String message) {
        super(message);
    }
}
