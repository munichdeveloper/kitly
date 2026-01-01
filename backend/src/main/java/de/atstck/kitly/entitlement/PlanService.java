package de.atstck.kitly.entitlement;

import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.entity.EntitlementDefinition;
import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.entity.PlanEntitlement;
import de.atstck.kitly.repository.EntitlementDefinitionRepository;
import de.atstck.kitly.repository.PlanEntitlementRepository;
import de.atstck.kitly.repository.PlanRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing dynamic plan configurations.
 * This service handles database-backed plan definitions and their entitlements.
 */
@Service
public class PlanService {

    private static final Logger log = LoggerFactory.getLogger(PlanService.class);

    private final PlanRepository planRepository;
    private final EntitlementDefinitionRepository entitlementDefinitionRepository;
    private final PlanEntitlementRepository planEntitlementRepository;

    public PlanService(
            PlanRepository planRepository,
            EntitlementDefinitionRepository entitlementDefinitionRepository,
            PlanEntitlementRepository planEntitlementRepository) {
        this.planRepository = planRepository;
        this.entitlementDefinitionRepository = entitlementDefinitionRepository;
        this.planEntitlementRepository = planEntitlementRepository;
    }

    /**
     * Get plan entitlements as a map (key -> value)
     * Falls back to PlanCatalog if plan not found in database
     */
    @Transactional(readOnly = true)
    public Map<String, String> getPlanEntitlements(String planCode) {
        // Try to load from database
        Optional<PlanEntity> planOpt = planRepository.findByCodeWithEntitlements(planCode.toLowerCase());

        if (planOpt.isPresent()) {
            PlanEntity plan = planOpt.get();
            return plan.getEntitlements().stream()
                    .collect(Collectors.toMap(
                            pe -> pe.getEntitlementDefinition().getFullKey(),
                            PlanEntitlement::getValue,
                            (v1, v2) -> v1,
                            LinkedHashMap::new
                    ));
        }

        // Fallback to static catalog
        log.warn("Plan '{}' not found in database, falling back to static PlanCatalog", planCode);
        PlanCatalog.PlanDefinition catalogPlan = PlanCatalog.getPlan(planCode);
        if (catalogPlan != null) {
            return catalogPlan.getEntitlements();
        }

        throw new ResourceNotFoundException("Plan not found: " + planCode);
    }

    /**
     * Get plan by code
     */
    @Transactional(readOnly = true)
    public PlanEntity getPlan(String planCode) {
        return planRepository.findByCodeIgnoreCase(planCode)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planCode));
    }

    /**
     * Get all active plans
     */
    @Transactional(readOnly = true)
    public List<PlanEntity> getActivePlans() {
        return planRepository.findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    /**
     * Create a new plan
     */
    @Transactional
    public PlanEntity createPlan(String code, String name, String description) {
        if (planRepository.findByCodeIgnoreCase(code).isPresent()) {
            throw new IllegalArgumentException("Plan with code '" + code + "' already exists");
        }

        PlanEntity plan = PlanEntity.builder()
                .code(code.toLowerCase())
                .name(name)
                .description(description)
                .isActive(true)
                .build();

        return planRepository.save(plan);
    }

    /**
     * Update a plan
     */
    @Transactional
    public PlanEntity updatePlan(UUID planId, String name, String description, Boolean isActive) {
        PlanEntity plan = planRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found"));

        if (name != null) {
            plan.setName(name);
        }
        if (description != null) {
            plan.setDescription(description);
        }
        if (isActive != null) {
            plan.setIsActive(isActive);
        }

        return planRepository.save(plan);
    }

    /**
     * Add or update an entitlement for a plan
     */
    @Transactional
    public PlanEntitlement setEntitlementValue(String planCode, EntitlementType type, String name, String value) {
        PlanEntity plan = getPlan(planCode);

        // Get or create entitlement definition
        EntitlementDefinition definition = entitlementDefinitionRepository
                .findByTypeAndName(type, name)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Entitlement definition not found: " + type.buildKey(name)));

        // Check if this entitlement already exists for this plan
        Optional<PlanEntitlement> existingOpt = plan.getEntitlements().stream()
                .filter(pe -> pe.getEntitlementDefinition().getId().equals(definition.getId()))
                .findFirst();

        if (existingOpt.isPresent()) {
            // Update existing
            PlanEntitlement existing = existingOpt.get();
            existing.setValue(value);
            return planEntitlementRepository.save(existing);
        } else {
            // Create new
            PlanEntitlement newEntitlement = PlanEntitlement.builder()
                    .plan(plan)
                    .entitlementDefinition(definition)
                    .value(value)
                    .build();
            plan.addEntitlement(newEntitlement);
            return planEntitlementRepository.save(newEntitlement);
        }
    }

    /**
     * Remove an entitlement from a plan
     */
    @Transactional
    public void removeEntitlement(String planCode, EntitlementType type, String name) {
        PlanEntity plan = getPlan(planCode);

        EntitlementDefinition definition = entitlementDefinitionRepository
                .findByTypeAndName(type, name)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Entitlement definition not found: " + type.buildKey(name)));

        plan.getEntitlements().removeIf(pe ->
                pe.getEntitlementDefinition().getId().equals(definition.getId()));

        planRepository.save(plan);
    }

    /**
     * Check if a plan exists
     */
    @Transactional(readOnly = true)
    public boolean planExists(String planCode) {
        return planRepository.findByCodeIgnoreCase(planCode).isPresent() ||
               PlanCatalog.planExists(planCode);
    }
}

