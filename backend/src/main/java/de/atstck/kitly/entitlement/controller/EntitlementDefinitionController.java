package de.atstck.kitly.entitlement.controller;

import de.atstck.kitly.entity.EntitlementDefinition;
import de.atstck.kitly.entitlement.EntitlementDefinitionService;
import de.atstck.kitly.entitlement.EntitlementType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for managing entitlement definitions.
 * Only accessible by platform administrators.
 */
@RestController
@RequestMapping("/api/admin/entitlement-definitions")
@Tag(name = "Entitlement Definition Management", description = "Manage entitlement definitions")
@SecurityRequirement(name = "Bearer Authentication")
@PreAuthorize("hasRole('PLATFORM_ADMIN')")
public class EntitlementDefinitionController {

    private final EntitlementDefinitionService service;

    public EntitlementDefinitionController(EntitlementDefinitionService service) {
        this.service = service;
    }

    @GetMapping
    @Operation(summary = "Get all entitlement definitions")
    public ResponseEntity<List<EntitlementDefinition>> getAllDefinitions() {
        return ResponseEntity.ok(service.getAllDefinitions());
    }

    @GetMapping("/type/{type}")
    @Operation(summary = "Get entitlement definitions by type")
    public ResponseEntity<List<EntitlementDefinition>> getDefinitionsByType(
            @PathVariable EntitlementType type) {
        return ResponseEntity.ok(service.getDefinitionsByType(type));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get entitlement definition by ID")
    public ResponseEntity<EntitlementDefinition> getDefinitionById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.getDefinitionById(id));
    }

    @PostMapping
    @Operation(summary = "Create a new entitlement definition")
    public ResponseEntity<EntitlementDefinition> createDefinition(
            @Valid @RequestBody CreateDefinitionRequest request) {
        EntitlementDefinition definition = service.createDefinition(
                request.getType(),
                request.getName(),
                request.getDisplayName(),
                request.getDescription(),
                request.getDefaultValue()
        );
        return ResponseEntity.ok(definition);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an entitlement definition")
    public ResponseEntity<EntitlementDefinition> updateDefinition(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDefinitionRequest request) {
        EntitlementDefinition definition = service.updateDefinition(
                id,
                request.getDisplayName(),
                request.getDescription(),
                request.getDefaultValue()
        );
        return ResponseEntity.ok(definition);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete an entitlement definition")
    public ResponseEntity<Void> deleteDefinition(@PathVariable UUID id) {
        service.deleteDefinition(id);
        return ResponseEntity.ok().build();
    }

    @Data
    public static class CreateDefinitionRequest {
        @NotNull
        private EntitlementType type;

        @NotBlank
        private String name;

        private String displayName;
        private String description;
        private String defaultValue;
    }

    @Data
    public static class UpdateDefinitionRequest {
        private String displayName;
        private String description;
        private String defaultValue;
    }
}

