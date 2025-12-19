package com.kitly.saas.controller;

import com.kitly.saas.security.annotation.TenantAccessCheck;
import com.kitly.saas.service.EntitlementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class EntitlementController {
    
    private final EntitlementService entitlementService;
    
    public EntitlementController(EntitlementService entitlementService) {
        this.entitlementService = entitlementService;
    }
    
    @GetMapping("/plans")
    public ResponseEntity<Map<String, EntitlementService.PlanInfo>> getPlanCatalog() {
        Map<String, EntitlementService.PlanInfo> catalog = entitlementService.getPlanCatalog();
        return ResponseEntity.ok(catalog);
    }
    
    @GetMapping("/tenants/{tenantId}/entitlements")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    @TenantAccessCheck
    public ResponseEntity<EntitlementService.Entitlements> getTenantEntitlements(@PathVariable UUID tenantId) {
        EntitlementService.Entitlements entitlements = entitlementService.getTenantEntitlements(tenantId);
        return ResponseEntity.ok(entitlements);
    }
}
