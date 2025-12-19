package com.kitly.saas.entitlement;

import java.util.*;

/**
 * Static catalog of subscription plans and their entitlements.
 * Plans define feature flags, limits, and quotas for tenants.
 */
public class PlanCatalog {
    
    private static final Map<String, PlanDefinition> PLANS = new HashMap<>();
    
    static {
        // Starter Plan
        PLANS.put("starter", PlanDefinition.builder()
                .code("starter")
                .name("Starter")
                .entitlements(Map.of(
                    "features.ai_assistant", "false",
                    "limits.projects", "10",
                    "limits.api_calls_per_month", "1000"
                ))
                .build());
        
        // Pro Plan
        PLANS.put("pro", PlanDefinition.builder()
                .code("pro")
                .name("Professional")
                .entitlements(Map.of(
                    "features.ai_assistant", "true",
                    "limits.projects", "100",
                    "limits.api_calls_per_month", "10000"
                ))
                .build());
        
        // Enterprise Plan
        PLANS.put("enterprise", PlanDefinition.builder()
                .code("enterprise")
                .name("Enterprise")
                .entitlements(Map.of(
                    "features.ai_assistant", "true",
                    "limits.projects", "unlimited",
                    "limits.api_calls_per_month", "unlimited"
                ))
                .build());
    }
    
    /**
     * Get plan definition by plan code
     */
    public static PlanDefinition getPlan(String planCode) {
        if (planCode == null) {
            return null;
        }
        return PLANS.get(planCode.toLowerCase());
    }
    
    /**
     * Get all available plans
     */
    public static Map<String, PlanDefinition> getAllPlans() {
        return Collections.unmodifiableMap(PLANS);
    }
    
    /**
     * Check if a plan code exists
     */
    public static boolean planExists(String planCode) {
        return planCode != null && PLANS.containsKey(planCode.toLowerCase());
    }
    
    /**
     * Plan definition containing entitlements
     */
    public static class PlanDefinition {
        private final String code;
        private final String name;
        private final Map<String, String> entitlements;
        
        private PlanDefinition(Builder builder) {
            this.code = builder.code;
            this.name = builder.name;
            this.entitlements = Collections.unmodifiableMap(builder.entitlements);
        }
        
        public String getCode() {
            return code;
        }
        
        public String getName() {
            return name;
        }
        
        public Map<String, String> getEntitlements() {
            return entitlements;
        }
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private String code;
            private String name;
            private Map<String, String> entitlements = new HashMap<>();
            
            public Builder code(String code) {
                this.code = code;
                return this;
            }
            
            public Builder name(String name) {
                this.name = name;
                return this;
            }
            
            public Builder entitlements(Map<String, String> entitlements) {
                this.entitlements = new HashMap<>(entitlements);
                return this;
            }
            
            public PlanDefinition build() {
                return new PlanDefinition(this);
            }
        }
    }
}
