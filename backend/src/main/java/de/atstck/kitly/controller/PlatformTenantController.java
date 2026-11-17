package de.atstck.kitly.controller;

import java.util.stream.Collectors;
import de.atstck.kitly.common.exception.BadRequestException;
import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.entity.Subscription;
import de.atstck.kitly.entity.Tenant;
import de.atstck.kitly.entitlement.EntitlementService;
import de.atstck.kitly.repository.PlanRepository;
import de.atstck.kitly.repository.SubscriptionRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import de.atstck.kitly.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/platform/tenants")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
@RequiredArgsConstructor
public class PlatformTenantController {

    private final TenantRepository tenantRepository;

    private final SubscriptionRepository subscriptions;
    private final PlanRepository plans;
    private final EntitlementService entitlements;

    public record PlanAccess(UUID id, String code, String name, boolean available, boolean subscribed, boolean enabled) {}
    public record ChangeAccess(@NotNull Boolean enabled) {}

    public record TenantSummary(UUID id, String name, String slug, Tenant.TenantStatus status) {}

    @GetMapping
    public List<TenantSummary> listTenants() {
        return tenantRepository.findAll(Sort.by("name", "slug")).stream()
                .map(tenant -> new TenantSummary(tenant.getId(), tenant.getName(), tenant.getSlug(), tenant.getStatus()))
                .toList();
    }

    @GetMapping("/{tenantId}/plans")
    @Transactional(readOnly = true)
    public List<PlanAccess> listPlans(@PathVariable UUID tenantId) {
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        var subscribedCodes = subscriptions.findAccessSubscriptions(tenantId).stream()
                .map(Subscription::getPlanCode).collect(Collectors.toSet());
        return plans.findAllByOrderByDisplayOrderAsc().stream().map(plan -> {
            boolean subscribed = subscribedCodes.contains(plan.getCode());
            boolean enabled = tenant.getPlanAccess().getOrDefault(plan.getCode(), subscribed);
            return new PlanAccess(plan.getId(), plan.getCode(), plan.getName(), plan.getIsActive(), subscribed, enabled);
        }).filter(plan -> plan.available() || plan.subscribed() || plan.enabled()).toList();
    }

    @PutMapping("/{tenantId}/plans/{planId}")
    @Transactional
    public PlanAccess setPlan(@PathVariable UUID tenantId, @PathVariable UUID planId,
                              @Valid @RequestBody ChangeAccess change) {
        Tenant tenant = tenantRepository.lockById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        var plan = plans.findById(planId).orElseThrow(() -> new ResourceNotFoundException("Plan not found"));
        boolean subscribed = subscriptions.findAccessSubscriptions(tenantId).stream()
                .anyMatch(subscription -> planId.equals(subscription.getPlan().getId()));
        boolean enabled = change.enabled();
        String code = plan.getCode();
        if (enabled != tenant.getPlanAccess().getOrDefault(code, subscribed)) {
            if (enabled && !plan.getIsActive() && !subscribed) {
                throw new BadRequestException("This plan is no longer available");
            }
            if (enabled == subscribed) {
                tenant.getPlanAccess().remove(code);
            } else {
                tenant.getPlanAccess().put(code, enabled);
            }
            entitlements.bumpEntitlementVersion(tenantId);
        }
        return new PlanAccess(planId, code, plan.getName(), plan.getIsActive(), subscribed, enabled);
    }
}
