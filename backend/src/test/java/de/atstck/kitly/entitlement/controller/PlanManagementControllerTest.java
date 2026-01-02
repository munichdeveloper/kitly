package de.atstck.kitly.entitlement.controller;

import de.atstck.kitly.common.exception.ResourceNotFoundException;
import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.entitlement.EntitlementType;
import de.atstck.kitly.entitlement.PlanService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PlanManagementControllerTest {

    @Mock
    private PlanService planService;

    @InjectMocks
    private PlanManagementController planManagementController;

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
                .build();
    }

    @Test
    void testGetAllPlans() {
        // Given
        List<PlanEntity> plans = Collections.singletonList(testPlan);
        when(planService.getAllPlans()).thenReturn(plans);

        // When
        ResponseEntity<List<PlanEntity>> response = planManagementController.getAllPlans();

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertEquals("starter", response.getBody().get(0).getCode());
        verify(planService).getAllPlans();
    }

    @Test
    void testGetPlan_Success() {
        // Given
        when(planService.getPlan("starter")).thenReturn(testPlan);

        // When
        ResponseEntity<PlanEntity> response = planManagementController.getPlan("starter");

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("starter", response.getBody().getCode());
        assertEquals("Starter Plan", response.getBody().getName());
        verify(planService).getPlan("starter");
    }

    @Test
    void testGetPlan_NotFound() {
        // Given
        when(planService.getPlan("nonexistent")).thenThrow(new ResourceNotFoundException("Plan not found"));

        // When & Then
        assertThrows(ResourceNotFoundException.class, () -> planManagementController.getPlan("nonexistent"));
        verify(planService).getPlan("nonexistent");
    }

    @Test
    void testGetPlanEntitlements() {
        // Given
        Map<String, String> entitlements = new HashMap<>();
        entitlements.put("features.advanced_reports", "true");
        entitlements.put("limits.max_users", "10");
        when(planService.getPlanEntitlements("starter")).thenReturn(entitlements);

        // When
        ResponseEntity<Map<String, String>> response = planManagementController.getPlanEntitlements("starter");

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals("true", response.getBody().get("features.advanced_reports"));
        verify(planService).getPlanEntitlements("starter");
    }

    @Test
    void testCreatePlan_Success() {
        // Given
        PlanManagementController.CreatePlanRequest request = new PlanManagementController.CreatePlanRequest();
        request.setCode("premium");
        request.setName("Premium Plan");
        request.setDescription("Premium features");

        PlanEntity newPlan = PlanEntity.builder()
                .id(UUID.randomUUID())
                .code("premium")
                .name("Premium Plan")
                .description("Premium features")
                .isActive(true)
                .build();

        when(planService.createPlan("premium", "Premium Plan", "Premium features")).thenReturn(newPlan);

        // When
        ResponseEntity<PlanEntity> response = planManagementController.createPlan(request);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("premium", response.getBody().getCode());
        assertEquals("Premium Plan", response.getBody().getName());
        verify(planService).createPlan("premium", "Premium Plan", "Premium features");
    }

    @Test
    void testUpdatePlan_Success() {
        // Given
        PlanManagementController.UpdatePlanRequest request = new PlanManagementController.UpdatePlanRequest();
        request.setName("Updated Name");
        request.setDescription("Updated Description");
        request.setIsActive(false);

        PlanEntity updatedPlan = PlanEntity.builder()
                .id(testPlanId)
                .code("starter")
                .name("Updated Name")
                .description("Updated Description")
                .isActive(false)
                .build();

        when(planService.updatePlan(testPlanId, "Updated Name", "Updated Description", false))
                .thenReturn(updatedPlan);

        // When
        ResponseEntity<PlanEntity> response = planManagementController.updatePlan(testPlanId, request);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("Updated Name", response.getBody().getName());
        assertEquals("Updated Description", response.getBody().getDescription());
        assertFalse(response.getBody().getIsActive());
        verify(planService).updatePlan(testPlanId, "Updated Name", "Updated Description", false);
    }

    @Test
    void testUpdatePlan_PartialUpdate() {
        // Given
        PlanManagementController.UpdatePlanRequest request = new PlanManagementController.UpdatePlanRequest();
        request.setName("New Name");
        // description and isActive are null

        PlanEntity updatedPlan = PlanEntity.builder()
                .id(testPlanId)
                .code("starter")
                .name("New Name")
                .description("Basic starter plan")
                .isActive(true)
                .build();

        when(planService.updatePlan(testPlanId, "New Name", null, null)).thenReturn(updatedPlan);

        // When
        ResponseEntity<PlanEntity> response = planManagementController.updatePlan(testPlanId, request);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals("New Name", response.getBody().getName());
        verify(planService).updatePlan(testPlanId, "New Name", null, null);
    }

    @Test
    void testSetEntitlementValue_Success() {
        // Given
        PlanManagementController.SetEntitlementRequest request = new PlanManagementController.SetEntitlementRequest();
        request.setType(EntitlementType.FEATURE);
        request.setName("advanced_reports");
        request.setValue("true");

        // Mock service call (setEntitlementValue returns PlanEntitlement, but controller doesn't use it)
        when(planService.setEntitlementValue(
                "starter",
                EntitlementType.FEATURE,
                "advanced_reports",
                "true"
        )).thenReturn(null); // Controller doesn't use the return value

        // When
        ResponseEntity<Void> response = planManagementController.setEntitlementValue("starter", request);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(planService).setEntitlementValue("starter", EntitlementType.FEATURE, "advanced_reports", "true");
    }

    @Test
    void testRemoveEntitlement_Success() {
        // Given
        doNothing().when(planService).removeEntitlement(
                "starter",
                EntitlementType.FEATURE,
                "advanced_reports"
        );

        // When
        ResponseEntity<Void> response = planManagementController.removeEntitlement(
                "starter",
                EntitlementType.FEATURE,
                "advanced_reports"
        );

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(planService).removeEntitlement("starter", EntitlementType.FEATURE, "advanced_reports");
    }

    @Test
    void testDeletePlan_Success() {
        // Given
        doNothing().when(planService).deletePlan(testPlanId);

        // When
        ResponseEntity<Void> response = planManagementController.deletePlan(testPlanId);

        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        verify(planService).deletePlan(testPlanId);
    }

    @Test
    void testDeletePlan_NotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        doThrow(new ResourceNotFoundException("Plan not found"))
                .when(planService).deletePlan(nonExistentId);

        // When & Then
        assertThrows(ResourceNotFoundException.class,
            () -> planManagementController.deletePlan(nonExistentId));
        verify(planService).deletePlan(nonExistentId);
    }

    @Test
    void testCreatePlan_DuplicateCode() {
        // Given
        PlanManagementController.CreatePlanRequest request = new PlanManagementController.CreatePlanRequest();
        request.setCode("starter");
        request.setName("Starter Plan");
        request.setDescription("Test");

        when(planService.createPlan("starter", "Starter Plan", "Test"))
                .thenThrow(new IllegalArgumentException("Plan with code 'starter' already exists"));

        // When & Then
        assertThrows(IllegalArgumentException.class,
            () -> planManagementController.createPlan(request));
        verify(planService).createPlan("starter", "Starter Plan", "Test");
    }

    @Test
    void testUpdatePlan_NotFound() {
        // Given
        UUID nonExistentId = UUID.randomUUID();
        PlanManagementController.UpdatePlanRequest request = new PlanManagementController.UpdatePlanRequest();
        request.setName("Test");

        when(planService.updatePlan(nonExistentId, "Test", null, null))
                .thenThrow(new ResourceNotFoundException("Plan not found"));

        // When & Then
        assertThrows(ResourceNotFoundException.class,
            () -> planManagementController.updatePlan(nonExistentId, request));
        verify(planService).updatePlan(nonExistentId, "Test", null, null);
    }
}

