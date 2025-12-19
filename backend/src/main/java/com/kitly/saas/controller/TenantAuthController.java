package com.kitly.saas.controller;

import com.kitly.saas.dto.AuthResponse;
import com.kitly.saas.service.TenantAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller for tenant-scoped authentication operations.
 * Allows users to switch tenant context and get tenant-scoped JWT tokens.
 */
@RestController
@RequestMapping("/api/tenants/{tenantId}/auth")
public class TenantAuthController {
    
    private final TenantAuthService tenantAuthService;
    
    public TenantAuthController(TenantAuthService tenantAuthService) {
        this.tenantAuthService = tenantAuthService;
    }
    
    /**
     * Generate a tenant-scoped JWT token for the current user.
     * This allows users to switch between tenants they are members of.
     */
    @PostMapping("/token")
    public ResponseEntity<AuthResponse> generateTenantToken(@PathVariable UUID tenantId,
                                                            Authentication authentication) {
        AuthResponse response = tenantAuthService.generateTenantToken(authentication.getName(), tenantId);
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get the current user's role in the tenant
     */
    @GetMapping("/role")
    public ResponseEntity<RoleResponse> getTenantRole(@PathVariable UUID tenantId,
                                                      Authentication authentication) {
        String role = tenantAuthService.getTenantRole(authentication.getName(), tenantId);
        return ResponseEntity.ok(new RoleResponse(role));
    }
    
    private record RoleResponse(String role) {}
}
