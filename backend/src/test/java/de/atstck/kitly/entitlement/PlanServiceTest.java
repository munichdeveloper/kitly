package de.atstck.kitly.entitlement;

import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.entity.EntitlementDefinition;
import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.entity.PlanEntitlement;
import de.atstck.kitly.repository.EntitlementDefinitionRepository;
import de.atstck.kitly.repository.PlanEntitlementRepository;
import de.atstck.kitly.repository.PlanRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PlanServiceTest {

    @Mock
    private PlanRepository planRepository;

    @Mock
    private EntitlementDefinitionRepository entitlementDefinitionRepository;

    @Mock
    private PlanEntitlementRepository planEntitlementRepository;

    @InjectMocks
    private PlanService planService;

    private PlanEntity testPlan;
    private UUID testPlanId;

    @BeforeEach
    void setUp() {
        testPlanId = UUID.randomUUID();
        testPlan = PlanEntity.builder()
                .id(testPlanId)
                .code("starter")
                .name("Starter Plan")
                .description("Basic starter plan")
                .isActive(true)
                .displayOrder(1)
                .entitlements(new ArrayList<>())
                .build();
    }

    @Test
    void testGetPlan_Success() {
        // Given
        when(planRepository.findByCodeIgnoreCase("starter")).thenReturn(Optional.of(testPlan));

        // When
        PlanEntity result = planService.getPlan("starter");

        // Then
        assertNotNull(result);
        assertEquals("starter", result.getCode());
        assertEquals("Starter Plan", result.getName());
        verify(planRepository).findByCodeIgnoreCase("starter");
    }

    @Test
    void testGetPlan_NotFound() {
        // Given
        when(planRepository.findByCodeIgnoreCase("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> planService.getPlan("nonexistent"));
        verify(planRepository).findByCodeIgnoreCase("nonexistent");
    }

    @Test
    void testGetAllPlans() {
        // Given
        List<PlanEntity> plans = Collections.singletonList(testPlan);
        when(planRepository.findAllByOrderByDisplayOrderAsc()).thenReturn(plans);

        // When
        List<PlanEntity> result = planService.getAllPlans();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("starter", result.get(0).getCode());
        verify(planRepository).findAllByOrderByDisplayOrderAsc();
    }

    @Test
    void testGetActivePlans() {
        // Given
        List<PlanEntity> plans = Collections.singletonList(testPlan);
        when(planRepository.findByIsActiveTrueOrderByDisplayOrderAsc()).thenReturn(plans);

        // When
        List<PlanEntity> result = planService.getActivePlans();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertTrue(result.get(0).getIsActive());
        verify(planRepository).findByIsActiveTrueOrderByDisplayOrderAsc();
    }

    @Test
    void testCreatePlan_Success() {
        // Given
        when(planRepository.findByCodeIgnoreCase("premium")).thenReturn(Optional.empty());
        when(planRepository.save(any(PlanEntity.class))).thenAnswer(invocation -> {
            PlanEntity plan = invocation.getArgument(0);
            plan.setId(UUID.randomUUID());
            return plan;
        });

        // When
        PlanEntity result = planService.createPlan("premium", "Premium Plan", "Premium features");

        // Then
        assertNotNull(result);
        assertEquals("premium", result.getCode());
        assertEquals("Premium Plan", result.getName());
        assertEquals("Premium features", result.getDescription());
        assertTrue(result.getIsActive());
        verify(planRepository).findByCodeIgnoreCase("premium");
        verify(planRepository).save(any(PlanEntity.class));
    }

    @Test
    void testCreatePlan_AlreadyExists() {
        // Given
        when(planRepository.findByCodeIgnoreCase("starter")).thenReturn(Optional.of(testPlan));

        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> planService.createPlan("starter", "Starter", "Desc"));
        verify(planRepository).findByCodeIgnoreCase("starter");
        verify(planRepository, never()).save(any());
    }

    @Test
    void testUpdatePlan_Success() {
        // Given
        when(planRepository.findById(testPlanId)).thenReturn(Optional.of(testPlan));
        when(planRepository.save(any(PlanEntity.class))).thenReturn(testPlan);

        // When
        PlanEntity result = planService.updatePlan(
                testPlanId,
                "Updated Name",
                "Updated Description",
                false
        );

        // Then
        assertNotNull(result);
        assertEquals("Updated Name", result.getName());
        assertEquals("Updated Description", result.getDescription());
        assertFalse(result.getIsActive());
        verify(planRepository).findById(testPlanId);
        verify(planRepository).save(testPlan);
    }

    @Test
    void testUpdatePlan_PartialUpdate() {
        // Given
        when(planRepository.findById(testPlanId)).thenReturn(Optional.of(testPlan));
        when(planRepository.save(any(PlanEntity.class))).thenReturn(testPlan);

        // When - only update name
        PlanEntity result = planService.updatePlan(testPlanId, "New Name", null, null);

        // Then
        assertNotNull(result);
        assertEquals("New Name", result.getName());
        assertEquals("Basic starter plan", result.getDescription()); // unchanged
        assertTrue(result.getIsActive()); // unchanged
        verify(planRepository).save(testPlan);
    }

    @Test
    void testUpdatePlan_NotFound() {
        // Given
        when(planRepository.findById(testPlanId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class,
            () -> planService.updatePlan(testPlanId, "Name", "Desc", true));
        verify(planRepository).findById(testPlanId);
        verify(planRepository, never()).save(any());
    }

    @Test
    void testDeletePlan_Success() {
        // Given
        when(planRepository.findById(testPlanId)).thenReturn(Optional.of(testPlan));
        doNothing().when(planRepository).delete(testPlan);

        // When
        planService.deletePlan(testPlanId);

        // Then
        verify(planRepository).findById(testPlanId);
        verify(planRepository).delete(testPlan);
    }

    @Test
    void testDeletePlan_NotFound() {
        // Given
        when(planRepository.findById(testPlanId)).thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> planService.deletePlan(testPlanId));
        verify(planRepository).findById(testPlanId);
        verify(planRepository, never()).delete(any());
    }

    @Test
    void testSetEntitlementValue_NewEntitlement() {
        // Given
        EntitlementDefinition definition = EntitlementDefinition.builder()
                .id(UUID.randomUUID())
                .type(EntitlementType.FEATURE)
                .name("advanced_reports")
                .displayName("Advanced Reports")
                .build();

        when(planRepository.findByCodeIgnoreCase("starter")).thenReturn(Optional.of(testPlan));
        when(entitlementDefinitionRepository.findByTypeAndName(EntitlementType.FEATURE, "advanced_reports"))
                .thenReturn(Optional.of(definition));
        when(planEntitlementRepository.save(any(PlanEntitlement.class))).thenAnswer(i -> i.getArgument(0));

        // When
        PlanEntitlement result = planService.setEntitlementValue(
                "starter",
                EntitlementType.FEATURE,
                "advanced_reports",
                "true"
        );

        // Then
        assertNotNull(result);
        assertEquals("true", result.getValue());
        assertEquals(definition, result.getEntitlementDefinition());
        verify(planEntitlementRepository).save(any(PlanEntitlement.class));
    }

    @Test
    void testRemoveEntitlement_Success() {
        // Given
        EntitlementDefinition definition = EntitlementDefinition.builder()
                .id(UUID.randomUUID())
                .type(EntitlementType.FEATURE)
                .name("advanced_reports")
                .build();

        PlanEntitlement entitlement = PlanEntitlement.builder()
                .id(UUID.randomUUID())
                .plan(testPlan)
                .entitlementDefinition(definition)
                .value("true")
                .build();

        testPlan.getEntitlements().add(entitlement);

        when(planRepository.findByCodeIgnoreCase("starter")).thenReturn(Optional.of(testPlan));
        when(entitlementDefinitionRepository.findByTypeAndName(EntitlementType.FEATURE, "advanced_reports"))
                .thenReturn(Optional.of(definition));
        when(planRepository.save(any(PlanEntity.class))).thenReturn(testPlan);

        // When
        planService.removeEntitlement("starter", EntitlementType.FEATURE, "advanced_reports");

        // Then
        assertTrue(testPlan.getEntitlements().isEmpty());
        verify(planRepository).save(testPlan);
    }

    @Test
    void testRemoveEntitlement_DefinitionNotFound() {
        // Given
        when(planRepository.findByCodeIgnoreCase("starter")).thenReturn(Optional.of(testPlan));
        when(entitlementDefinitionRepository.findByTypeAndName(EntitlementType.FEATURE, "nonexistent"))
                .thenReturn(Optional.empty());

        // When & Then
        assertThrows(ResourceNotFoundException.class,
            () -> planService.removeEntitlement("starter", EntitlementType.FEATURE, "nonexistent"));
        verify(planRepository, never()).save(any());
    }

    @Test
    void testPlanExists_InDatabase() {
        // Given
        when(planRepository.findByCodeIgnoreCase("starter")).thenReturn(Optional.of(testPlan));

        // When
        boolean result = planService.planExists("starter");

        // Then
        assertTrue(result);
        verify(planRepository).findByCodeIgnoreCase("starter");
    }

    @Test
    void testPlanExists_NotFound() {
        // Given
        when(planRepository.findByCodeIgnoreCase("nonexistent")).thenReturn(Optional.empty());

        // When
        planService.planExists("nonexistent");

        // Then - will return false or check static catalog
        verify(planRepository).findByCodeIgnoreCase("nonexistent");
    }
}

