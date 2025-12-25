package com.kitly.saas.controller;

import com.kitly.saas.config.StripeConfig;
import com.kitly.saas.dto.PlatformSettingDTO;
import com.kitly.saas.dto.PlatformSettingRequest;
import com.kitly.saas.entity.User;
import com.kitly.saas.repository.UserRepository;
import com.kitly.saas.service.PlatformSettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform/settings")
@RequiredArgsConstructor
public class PlatformAdminController {

    private final PlatformSettingService platformSettingService;
    private final StripeConfig stripeConfig;
    private final UserRepository userRepository;

    @GetMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<PlatformSettingDTO>> getAllSettings() {
        List<PlatformSettingDTO> settings = platformSettingService.getAllSettings();
        return ResponseEntity.ok(settings);
    }

    @GetMapping("/{key}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PlatformSettingDTO> getSetting(@PathVariable String key) {
        PlatformSettingDTO setting = platformSettingService.getSetting(key);
        return ResponseEntity.ok(setting);
    }

    @PostMapping
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PlatformSettingDTO> createOrUpdateSetting(
            @Valid @RequestBody PlatformSettingRequest request,
            Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        PlatformSettingDTO setting = platformSettingService.createOrUpdateSetting(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(setting);
    }

    @PutMapping("/{key}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<PlatformSettingDTO> updateSetting(
            @PathVariable String key,
            @Valid @RequestBody PlatformSettingRequest request,
            Authentication authentication) {
        request.setKey(key);
        UUID userId = getUserIdFromAuthentication(authentication);
        PlatformSettingDTO setting = platformSettingService.createOrUpdateSetting(request, userId);
        return ResponseEntity.ok(setting);
    }

    @DeleteMapping("/{key}")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Void> deleteSetting(@PathVariable String key) {
        platformSettingService.deleteSetting(key);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/bulk")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<List<PlatformSettingDTO>> bulkUpdateSettings(
            @Valid @RequestBody List<PlatformSettingRequest> requests,
            Authentication authentication) {
        UUID userId = getUserIdFromAuthentication(authentication);
        List<PlatformSettingDTO> settings = platformSettingService.bulkUpdateSettings(requests, userId);
        return ResponseEntity.ok(settings);
    }

    @PostMapping("/stripe/switch-mode")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, String>> switchStripeMode(
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String newMode = request.get("mode");

        if (!"test".equals(newMode) && !"live".equals(newMode)) {
            Map<String, String> error = new HashMap<>();
            error.put("error", "Invalid mode. Must be 'test' or 'live'");
            return ResponseEntity.badRequest().body(error);
        }

        UUID userId = getUserIdFromAuthentication(authentication);

        PlatformSettingRequest settingRequest = PlatformSettingRequest.builder()
                .key("stripe.mode")
                .value(newMode)
                .type(com.kitly.saas.entity.PlatformSetting.SettingType.STRING)
                .description("Stripe API mode: test or live")
                .isEncrypted(false)
                .build();

        platformSettingService.createOrUpdateSetting(settingRequest, userId);

        // Refresh Stripe configuration
        stripeConfig.refreshStripeConfig();

        Map<String, String> response = new HashMap<>();
        response.put("message", "Stripe mode switched to " + newMode);
        response.put("mode", newMode);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/stripe/current-mode")
    @PreAuthorize("hasRole('PLATFORM_ADMIN')")
    public ResponseEntity<Map<String, String>> getCurrentStripeMode() {
        String currentMode = stripeConfig.getCurrentMode();
        Map<String, String> response = new HashMap<>();
        response.put("mode", currentMode);
        return ResponseEntity.ok(response);
    }

    private UUID getUserIdFromAuthentication(Authentication authentication) {
        String username = authentication.getName();
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found: " + username));
        return user.getId();
    }
}

