package de.atstck.kitly.entitlement.controller;

import de.atstck.kitly.entitlement.EntitlementResponse;
import de.atstck.kitly.entitlement.EntitlementService;
import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.repository.TenantRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/**
 * Service-to-service entitlement lookup for connected apps (e.g. the Controla Premium backend).
 *
 * Apps need a tenant's plan for background work that runs without a user token, and must notice
 * cancellations without waiting for the next login. Authenticated by a shared service key,
 * never by a user JWT. Without configured keys the endpoint does not exist (404). Read-only.
 */
@RestController
@RequestMapping("/api/internal")
public class InternalEntitlementController {

    static final String KEY_HEADER = "X-Kitly-Service-Key";
    static final int MAX_TENANTS = 500;
    private static final Logger log = LoggerFactory.getLogger(InternalEntitlementController.class);

    public enum LookupStatus { ACTIVE, NO_SUBSCRIPTION, UNKNOWN_TENANT }

    public record LookupRequest(List<UUID> tenantIds) { }

    public record TenantEntitlements(UUID tenantId, LookupStatus status, String planCode, String subscriptionStatus,
                                     Long entitlementVersion, List<EntitlementResponse.EntitlementItem> items) { }

    public record LookupResponse(List<TenantEntitlements> tenants) { }

    private final EntitlementService entitlementService;
    private final TenantRepository tenantRepository;
    private final List<byte[]> serviceKeys;

    public InternalEntitlementController(EntitlementService entitlementService, TenantRepository tenantRepository,
                                         @Value("${kitly.internal.service-keys:}") String serviceKeys) {
        this.entitlementService = entitlementService;
        this.tenantRepository = tenantRepository;
        this.serviceKeys = Arrays.stream(serviceKeys == null ? new String[0] : serviceKeys.split(","))
                .map(String::trim)
                .filter(key -> key.length() >= 32)
                .map(key -> key.getBytes(StandardCharsets.UTF_8))
                .toList();
    }

    @PostMapping("/entitlements/lookup")
    public ResponseEntity<LookupResponse> lookup(@RequestHeader(value = KEY_HEADER, required = false) String key,
                                                 @RequestBody(required = false) LookupRequest request) {
        if (serviceKeys.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        if (!authorized(key)) {
            return ResponseEntity.status(401).cacheControl(CacheControl.noStore()).build();
        }
        if (request == null || request.tenantIds() == null || request.tenantIds().size() > MAX_TENANTS) {
            return ResponseEntity.badRequest().build();
        }

        List<TenantEntitlements> result = new ArrayList<>();
        for (UUID tenantId : new LinkedHashSet<>(request.tenantIds())) {
            if (tenantId != null) result.add(lookup(tenantId));
        }
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new LookupResponse(result));
    }

    private TenantEntitlements lookup(UUID tenantId) {
        if (!tenantRepository.existsById(tenantId)) {
            return new TenantEntitlements(tenantId, LookupStatus.UNKNOWN_TENANT, null, null, null, List.of());
        }
        try {
            EntitlementResponse entitlements = entitlementService.computeEntitlements(tenantId);
            return new TenantEntitlements(tenantId, LookupStatus.ACTIVE, entitlements.getPlanCode(),
                    entitlements.getStatus(), entitlements.getEntitlementVersion(),
                    entitlements.getItems() == null ? List.of() : entitlements.getItems());
        } catch (ResourceNotFoundException | IllegalStateException e) {
            // No ACTIVE/TRIALING subscription, or one without a usable plan: apps must revoke paid rights.
            return new TenantEntitlements(tenantId, LookupStatus.NO_SUBSCRIPTION, null, null, null, List.of());
        } catch (RuntimeException e) {
            // Unexpected failure for one tenant must not be reported as "no subscription".
            log.error("Internal entitlement lookup failed for tenant {}", tenantId, e);
            throw e;
        }
    }

    private boolean authorized(String key) {
        if (key == null) return false;
        byte[] candidate = key.trim().getBytes(StandardCharsets.UTF_8);
        return serviceKeys.stream().anyMatch(configured -> MessageDigest.isEqual(configured, candidate));
    }
}
