package com.kitly.saas.entitlement.controller;

import com.kitly.saas.common.context.TenantContextHolder;
import com.kitly.saas.entitlement.EntitlementResponse;
import com.kitly.saas.entitlement.EntitlementService;
import com.kitly.saas.entitlement.PlanCatalog;
import org.junit.jupiter.api.AfterEach;
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
class EntitlementControllerTest {
    
    @Mock
    private EntitlementService entitlementService;
    
    @InjectMocks
    private EntitlementController entitlementController;
    
    private UUID testTenantId;
    
    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
    }
    
    @AfterEach
    void tearDown() {
        TenantContextHolder.clear();
    }
    
    @Test
    void testGetPlanCatalog() {
        // When
        ResponseEntity<Map<String, PlanCatalog.PlanDefinition>> response = 
                entitlementController.getPlanCatalog();
        
        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        Map<String, PlanCatalog.PlanDefinition> catalog = response.getBody();
        assertNotNull(catalog);
        assertEquals(3, catalog.size());
        assertTrue(catalog.containsKey("starter"));
        assertTrue(catalog.containsKey("pro"));
        assertTrue(catalog.containsKey("enterprise"));
    }
    
    @Test
    void testGetTenantEntitlements() {
        // Given
        EntitlementResponse mockResponse = EntitlementResponse.builder()
                .tenantId(testTenantId)
                .planCode("pro")
                .status("ACTIVE")
                .seatsQuantity(50)
                .activeSeats(7L)
                .entitlementVersion(15L)
                .items(new ArrayList<>())
                .build();
        
        when(entitlementService.computeEntitlements(testTenantId))
                .thenReturn(mockResponse);
        
        // When
        ResponseEntity<EntitlementResponse> response = 
                entitlementController.getTenantEntitlements(testTenantId);
        
        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        EntitlementResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(testTenantId, body.getTenantId());
        assertEquals("pro", body.getPlanCode());
        assertEquals("ACTIVE", body.getStatus());
        assertEquals(50, body.getSeatsQuantity());
        assertEquals(7L, body.getActiveSeats());
        assertEquals(15L, body.getEntitlementVersion());
        
        verify(entitlementService, times(1)).computeEntitlements(testTenantId);
    }
    
    @Test
    void testGetMyEntitlements_WithTenantContext() {
        // Given
        TenantContextHolder.setTenantId(testTenantId);
        
        EntitlementResponse mockResponse = EntitlementResponse.builder()
                .tenantId(testTenantId)
                .planCode("starter")
                .status("ACTIVE")
                .seatsQuantity(10)
                .activeSeats(3L)
                .entitlementVersion(5L)
                .items(new ArrayList<>())
                .build();
        
        when(entitlementService.computeEntitlements(testTenantId))
                .thenReturn(mockResponse);
        
        // When
        ResponseEntity<EntitlementResponse> response = 
                entitlementController.getMyEntitlements();
        
        // Then
        assertNotNull(response);
        assertEquals(200, response.getStatusCode().value());
        
        EntitlementResponse body = response.getBody();
        assertNotNull(body);
        assertEquals(testTenantId, body.getTenantId());
        assertEquals("starter", body.getPlanCode());
        
        verify(entitlementService, times(1)).computeEntitlements(testTenantId);
    }
    
    @Test
    void testGetMyEntitlements_NoTenantContext() {
        // Given - no tenant context set
        
        // When
        ResponseEntity<EntitlementResponse> response = 
                entitlementController.getMyEntitlements();
        
        // Then
        assertNotNull(response);
        assertEquals(400, response.getStatusCode().value());
        assertNull(response.getBody());
        
        verify(entitlementService, never()).computeEntitlements(any());
    }
}
