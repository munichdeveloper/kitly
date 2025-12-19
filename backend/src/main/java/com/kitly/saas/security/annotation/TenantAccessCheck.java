package com.kitly.saas.security.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark endpoints that require tenant access validation.
 * The aspect will verify that the tenantId path variable matches the tenant ID in TenantContext.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface TenantAccessCheck {
}
