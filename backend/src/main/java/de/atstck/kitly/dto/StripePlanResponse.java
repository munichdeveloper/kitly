package de.atstck.kitly.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StripePlanResponse {
    private String stripeId;
    private String planName;
    private Long unitAmount;
    private String currency;
    private String interval;
    private String type;
    private String formattedPrice;
}

