package de.atstck.kitly.entitlement;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
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
    /** End of the paid period (UTC) as last reported by Stripe; null if unknown. */
    private Instant currentPeriodEnd;
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
