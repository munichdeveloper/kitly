package com.kitly.saas.controller;

import com.kitly.saas.dto.TenantRequest;
import com.kitly.saas.dto.TenantResponse;
import com.kitly.saas.security.annotation.TenantAccessCheck;
import com.kitly.saas.service.TenantService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class TenantController {
    
    private final TenantService tenantService;
    
    public TenantController(TenantService tenantService) {
        this.tenantService = tenantService;
    }
    
    @PostMapping("/tenants")
    public ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody TenantRequest request,
                                                       Authentication authentication) {
        TenantResponse tenant = tenantService.createTenant(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(tenant);
    }
    
    @GetMapping("/tenants/{tenantId}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    @TenantAccessCheck
    public ResponseEntity<TenantResponse> getTenant(@PathVariable UUID tenantId) {
        TenantResponse tenant = tenantService.getTenantById(tenantId);
        return ResponseEntity.ok(tenant);
    }
    
    @GetMapping("/me/tenants")
    public ResponseEntity<List<TenantResponse>> getCurrentUserTenants(Authentication authentication) {
        List<TenantResponse> tenants = tenantService.getUserTenants(authentication.getName());
        return ResponseEntity.ok(tenants);
    }
}
