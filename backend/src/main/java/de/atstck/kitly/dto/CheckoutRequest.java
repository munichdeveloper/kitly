package de.atstck.kitly.dto;

import de.atstck.kitly.entity.Subscription;
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

