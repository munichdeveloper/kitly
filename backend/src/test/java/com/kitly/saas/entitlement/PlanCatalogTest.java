package com.kitly.saas.entitlement;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PlanCatalogTest {
    
    @Test
    void testGetPlan_Starter() {
        PlanCatalog.PlanDefinition plan = PlanCatalog.getPlan("starter");
        
        assertNotNull(plan);
        assertEquals("starter", plan.getCode());
        assertEquals("Starter", plan.getName());
        
        Map<String, String> entitlements = plan.getEntitlements();
        assertNotNull(entitlements);
        assertEquals("false", entitlements.get("features.ai_assistant"));
        assertEquals("10", entitlements.get("limits.projects"));
        assertEquals("1000", entitlements.get("limits.api_calls_per_month"));
    }
    
    @Test
    void testGetPlan_Pro() {
        PlanCatalog.PlanDefinition plan = PlanCatalog.getPlan("pro");
        
        assertNotNull(plan);
        assertEquals("pro", plan.getCode());
        assertEquals("Professional", plan.getName());
        
        Map<String, String> entitlements = plan.getEntitlements();
        assertNotNull(entitlements);
        assertEquals("true", entitlements.get("features.ai_assistant"));
        assertEquals("100", entitlements.get("limits.projects"));
        assertEquals("10000", entitlements.get("limits.api_calls_per_month"));
    }
    
    @Test
    void testGetPlan_Enterprise() {
        PlanCatalog.PlanDefinition plan = PlanCatalog.getPlan("enterprise");
        
        assertNotNull(plan);
        assertEquals("enterprise", plan.getCode());
        assertEquals("Enterprise", plan.getName());
        
        Map<String, String> entitlements = plan.getEntitlements();
        assertNotNull(entitlements);
        assertEquals("true", entitlements.get("features.ai_assistant"));
        assertEquals("unlimited", entitlements.get("limits.projects"));
        assertEquals("unlimited", entitlements.get("limits.api_calls_per_month"));
    }
    
    @Test
    void testGetPlan_CaseInsensitive() {
        PlanCatalog.PlanDefinition plan1 = PlanCatalog.getPlan("PRO");
        PlanCatalog.PlanDefinition plan2 = PlanCatalog.getPlan("Pro");
        PlanCatalog.PlanDefinition plan3 = PlanCatalog.getPlan("pro");
        
        assertNotNull(plan1);
        assertNotNull(plan2);
        assertNotNull(plan3);
        assertEquals("pro", plan1.getCode());
        assertEquals("pro", plan2.getCode());
        assertEquals("pro", plan3.getCode());
    }
    
    @Test
    void testGetPlan_InvalidPlan() {
        PlanCatalog.PlanDefinition plan = PlanCatalog.getPlan("invalid");
        
        assertNull(plan);
    }
    
    @Test
    void testGetPlan_NullInput() {
        PlanCatalog.PlanDefinition plan = PlanCatalog.getPlan(null);
        
        assertNull(plan);
    }
    
    @Test
    void testGetAllPlans() {
        Map<String, PlanCatalog.PlanDefinition> plans = PlanCatalog.getAllPlans();
        
        assertNotNull(plans);
        assertEquals(3, plans.size());
        assertTrue(plans.containsKey("starter"));
        assertTrue(plans.containsKey("pro"));
        assertTrue(plans.containsKey("enterprise"));
    }
    
    @Test
    void testGetAllPlans_IsUnmodifiable() {
        Map<String, PlanCatalog.PlanDefinition> plans = PlanCatalog.getAllPlans();
        
        assertThrows(UnsupportedOperationException.class, () -> {
            plans.put("newplan", PlanCatalog.PlanDefinition.builder()
                    .code("newplan")
                    .name("New Plan")
                    .build());
        });
    }
    
    @Test
    void testPlanExists() {
        assertTrue(PlanCatalog.planExists("starter"));
        assertTrue(PlanCatalog.planExists("pro"));
        assertTrue(PlanCatalog.planExists("enterprise"));
        assertTrue(PlanCatalog.planExists("STARTER"));
        
        assertFalse(PlanCatalog.planExists("invalid"));
        assertFalse(PlanCatalog.planExists(null));
    }
    
    @Test
    void testPlanEntitlementsAreUnmodifiable() {
        PlanCatalog.PlanDefinition plan = PlanCatalog.getPlan("pro");
        Map<String, String> entitlements = plan.getEntitlements();
        
        assertThrows(UnsupportedOperationException.class, () -> {
            entitlements.put("new.feature", "value");
        });
    }
}
