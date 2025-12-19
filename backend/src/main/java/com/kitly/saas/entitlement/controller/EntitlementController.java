package com.kitly.saas.entitlement.controller;

import com.kitly.saas.common.context.TenantContextHolder;
import com.kitly.saas.entitlement.EntitlementResponse;
import com.kitly.saas.entitlement.EntitlementService;
import com.kitly.saas.entitlement.PlanCatalog;
import com.kitly.saas.security.annotation.TenantAccessCheck;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for entitlement management
 */
@RestController
@RequestMapping("/api")
public class EntitlementController {
    
    private final EntitlementService entitlementService;
    
    public EntitlementController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }
    
    /**
     * GET /api/plans - Get catalog of available plans
     */
    @GetMapping("/plans")
    public ResponseEntity<Map<String, PlanCatalog.PlanDefinition>> getPlanCatalog() {
        return ResponseEntity.ok(PlanCatalog.getAllPlans());
    }
    
    /**
     * GET /api/tenants/{tenantId}/entitlements - Get entitlements for a specific tenant
     */
    @GetMapping("/tenants/{tenantId}/entitlements")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    @TenantAccessCheck
    public ResponseEntity<EntitlementResponse> getTenantEntitlements(@PathVariable UUID tenantId) {
        EntitlementResponse entitlements = entitlementService.computeEntitlements(tenantId);
        return ResponseEntity.ok(entitlements);
    }
    
    /**
     * GET /api/entitlements/me - Get entitlements for current tenant from TenantContext
     */
    @GetMapping("/entitlements/me")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    public ResponseEntity<EntitlementResponse> getMyEntitlements() {
        UUID tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return ResponseEntity.badRequest().build();
        }
        
        EntitlementResponse entitlements = entitlementService.computeEntitlements(tenantId);
        return ResponseEntity.ok(entitlements);
    }
}
