package com.kitly.saas.dto;

import com.kitly.saas.entity.Subscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionResponse {
    private UUID id;
    private UUID tenantId;
    private Subscription.SubscriptionPlan plan;
    private Subscription.SubscriptionStatus status;
    private Subscription.BillingCycle billingCycle;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocalDateTime trialEndsAt;
    private LocalDateTime cancelledAt;
}

