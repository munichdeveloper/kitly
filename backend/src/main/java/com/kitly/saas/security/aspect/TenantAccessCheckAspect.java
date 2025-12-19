package com.kitly.saas.security.aspect;

import com.kitly.saas.context.TenantContextHolder;
import com.kitly.saas.exception.TenantAccessDeniedException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PathVariable;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.UUID;

/**
 * Aspect that enforces tenant isolation by validating that the tenantId path variable
 * matches the tenant ID stored in TenantContext (from the JWT token).
 */
@Aspect
@Component
public class TenantAccessCheckAspect {
    
    @Before("@annotation(com.kitly.saas.security.annotation.TenantAccessCheck)")
    public void checkTenantAccess(JoinPoint joinPoint) {
        UUID contextTenantId = TenantContextHolder.getTenantId();
        
        if (contextTenantId == null) {
            throw new TenantAccessDeniedException("No tenant context found in request");
        }
        
        // Extract tenantId from method parameters
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        Parameter[] parameters = method.getParameters();
        Object[] args = joinPoint.getArgs();
        
        UUID pathTenantId = null;
        for (int i = 0; i < parameters.length; i++) {
            Parameter param = parameters[i];
            PathVariable pathVariable = param.getAnnotation(PathVariable.class);
            
            if (pathVariable != null) {
                String paramName = pathVariable.value().isEmpty() ? param.getName() : pathVariable.value();
                if ("tenantId".equals(paramName) && args[i] instanceof UUID) {
                    pathTenantId = (UUID) args[i];
                    break;
                }
            }
        }
        
        if (pathTenantId == null) {
            // If @TenantAccessCheck is present but no tenantId found, this is a misconfiguration
            throw new TenantAccessDeniedException(
                    "@TenantAccessCheck annotation requires a 'tenantId' path variable"
            );
        }
        
        if (!contextTenantId.equals(pathTenantId)) {
            throw new TenantAccessDeniedException(
                    "Access denied: Tenant ID in request does not match authenticated tenant"
            );
        }
    }
}
