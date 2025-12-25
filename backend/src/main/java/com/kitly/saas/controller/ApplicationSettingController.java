package com.kitly.saas.controller;

import com.kitly.saas.dto.ApplicationSettingDTO;
import com.kitly.saas.dto.ApplicationSettingRequest;
import com.kitly.saas.security.annotation.TenantAccessCheck;
import com.kitly.saas.service.ApplicationSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/tenants/{tenantId}/settings")
@RequiredArgsConstructor
public class ApplicationSettingController {

    private final ApplicationSettingService settingService;

    @GetMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    @TenantAccessCheck
    public ResponseEntity<List<ApplicationSettingDTO>> getAllSettings(@PathVariable UUID tenantId) {
        List<ApplicationSettingDTO> settings = settingService.getAllSettings(tenantId);
        return ResponseEntity.ok(settings);
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN', 'MEMBER')")
    @TenantAccessCheck
    public ResponseEntity<ApplicationSettingDTO> getSetting(
            @PathVariable UUID tenantId,
            @PathVariable String key) {
        ApplicationSettingDTO setting = settingService.getSetting(tenantId, key);
        return ResponseEntity.ok(setting);
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @TenantAccessCheck
    public ResponseEntity<ApplicationSettingDTO> createOrUpdateSetting(
            @PathVariable UUID tenantId,
            @Valid @RequestBody ApplicationSettingRequest request,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        ApplicationSettingDTO setting = settingService.createOrUpdateSetting(tenantId, request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(setting);
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @TenantAccessCheck
    public ResponseEntity<ApplicationSettingDTO> updateSetting(
            @PathVariable UUID tenantId,
            @PathVariable String key,
            @Valid @RequestBody ApplicationSettingRequest request,
            Authentication authentication) {

        // Ensure the key in the path matches the key in the request
        request.setKey(key);
        UUID userId = UUID.fromString(authentication.getName());
        ApplicationSettingDTO setting = settingService.createOrUpdateSetting(tenantId, request, userId);
        return ResponseEntity.ok(setting);
    }

    @DeleteMapping("/{key}")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @TenantAccessCheck
    public ResponseEntity<Void> deleteSetting(
            @PathVariable UUID tenantId,
            @PathVariable String key) {
        settingService.deleteSetting(tenantId, key);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasAnyRole('OWNER', 'ADMIN')")
    @TenantAccessCheck
    public ResponseEntity<List<ApplicationSettingDTO>> bulkUpdateSettings(
            @PathVariable UUID tenantId,
            @Valid @RequestBody List<ApplicationSettingRequest> requests,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        List<ApplicationSettingDTO> settings = settingService.bulkUpdateSettings(tenantId, requests, userId);
        return ResponseEntity.ok(settings);
    }
}

