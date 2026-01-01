package de.atstck.kitly.entitlement.controller;

import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.entitlement.PlanService;
import de.atstck.kitly.entitlement.EntitlementType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * REST controller for managing dynamic plan configurations.
 * Only accessible by platform administrators.
 */
@RestController
@RequestMapping("/api/admin/plans")
@Tag(name = "Plan Management", description = "Manage dynamic plan configurations")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class PlanManagementController {

    private final PlanService planService;

    public PlanManagementController(PlanService planService) {
        this.planService = planService;
    }

    @GetMapping
    @Operation(summary = "Get all active plans")
    public ResponseEntity<List<PlanEntity>> getAllPlans() {
        return ResponseEntity.ok(planService.getActivePlans());
    }

    @GetMapping("/{planCode}")
    @Operation(summary = "Get plan by code")
    public ResponseEntity<PlanEntity> getPlan(@PathVariable String planCode) {
        return ResponseEntity.ok(planService.getPlan(planCode));
    }

    @GetMapping("/{planCode}/entitlements")
    @Operation(summary = "Get plan entitlements")
    public ResponseEntity<Map<String, String>> getPlanEntitlements(@PathVariable String planCode) {
        return ResponseEntity.ok(planService.getPlanEntitlements(planCode));
    }

    @PostMapping
    @Operation(summary = "Create a new plan")
    public ResponseEntity<PlanEntity> createPlan(@Valid @RequestBody CreatePlanRequest request) {
        PlanEntity plan = planService.createPlan(
                request.getCode(),
                request.getName(),
                request.getDescription()
        );
        return ResponseEntity.ok(plan);
    }

    @PutMapping("/{planId}")
    @Operation(summary = "Update a plan")
    public ResponseEntity<PlanEntity> updatePlan(
            @PathVariable UUID planId,
            @Valid @RequestBody UpdatePlanRequest request) {
        PlanEntity plan = planService.updatePlan(
                planId,
                request.getName(),
                request.getDescription(),
                request.getIsActive()
        );
        return ResponseEntity.ok(plan);
    }

    @PutMapping("/{planCode}/entitlements")
    @Operation(summary = "Set entitlement value for a plan")
    public ResponseEntity<Void> setEntitlementValue(
            @PathVariable String planCode,
            @Valid @RequestBody SetEntitlementRequest request) {
        planService.setEntitlementValue(
                planCode,
                request.getType(),
                request.getName(),
                request.getValue()
        );
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{planCode}/entitlements")
    @Operation(summary = "Remove entitlement from a plan")
    public ResponseEntity<Void> removeEntitlement(
            @PathVariable String planCode,
            @RequestParam EntitlementType type,
            @RequestParam String name) {
        planService.removeEntitlement(planCode, type, name);
        return ResponseEntity.ok().build();
    }

    @Data
    public static class CreatePlanRequest {
        @NotBlank
        private String code;

        @NotBlank
        private String name;

        private String description;
    }

    @Data
    public static class UpdatePlanRequest {
        private String name;
        private String description;
        private Boolean isActive;
    }

    @Data
    public static class SetEntitlementRequest {
        @NotBlank
        private EntitlementType type;

        @NotBlank
        private String name;

        @NotBlank
        private String value;
    }
}

