package com.kitly.saas.dto;

import com.kitly.saas.entity.Subscription;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.UUID;

@Data
public class CheckoutRequest {
    @NotNull
    private UUID tenantId;

    @NotNull
    private Subscription.SubscriptionPlan plan;
}

