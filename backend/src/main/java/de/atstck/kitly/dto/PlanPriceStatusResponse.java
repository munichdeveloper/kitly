package de.atstck.kitly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanPriceStatusResponse {
    private String planName;
    private String priceId;
    private boolean active;
    private String status;
    private StripePlanResponse priceDetails;
    private String error;
}

