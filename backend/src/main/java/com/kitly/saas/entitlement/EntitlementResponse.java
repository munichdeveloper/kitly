package com.kitly.saas.entitlement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

/**
 * Response DTO for tenant entitlements with metadata
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EntitlementResponse {
    
    private UUID tenantId;
    private String planCode;
    private String status;
    private Integer seatsQuantity;
    private Long activeSeats;
    private Long entitlementVersion;
    private List<EntitlementItem> items;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EntitlementItem {
        private String key;
        private String value;
        private String source;
    }
}
