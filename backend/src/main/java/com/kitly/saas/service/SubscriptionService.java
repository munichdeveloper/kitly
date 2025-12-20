package com.kitly.saas.service;

import com.kitly.saas.dto.SubscriptionResponse;
import com.kitly.saas.entity.Subscription;
import com.kitly.saas.repository.SubscriptionRepository;
import com.kitly.saas.repository.TenantRepository;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
public class SubscriptionService {

    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;

    public SubscriptionService(SubscriptionRepository subscriptionRepository, TenantRepository tenantRepository) {
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
    }

    public SubscriptionResponse getCurrentSubscription(UUID tenantId) {
        // Check if tenant exists
        if (!tenantRepository.existsById(tenantId)) {
            throw new RuntimeException("Tenant not found");
        }

        // Find active subscription first
        return subscriptionRepository.findByTenantIdAndStatus(tenantId, Subscription.SubscriptionStatus.ACTIVE)
                .map(this::mapToResponse)
                .orElseGet(() -> {
                    // If no active subscription, find the most recent one (e.g. trialing, cancelled, etc.)
                    List<Subscription> subscriptions = subscriptionRepository.findByTenantId(tenantId);
                    return subscriptions.stream()
                            .max(Comparator.comparing(Subscription::getStartsAt))
                            .map(this::mapToResponse)
                            .orElse(null);
                });
    }

    private SubscriptionResponse mapToResponse(Subscription subscription) {
        return SubscriptionResponse.builder()
                .id(subscription.getId())
                .tenantId(subscription.getTenant().getId())
                .plan(subscription.getPlan())
                .status(subscription.getStatus())
                .billingCycle(subscription.getBillingCycle())
                .amount(subscription.getAmount())
                .currency(subscription.getCurrency())
                .startsAt(subscription.getStartsAt())
                .endsAt(subscription.getEndsAt())
                .trialEndsAt(subscription.getTrialEndsAt())
                .cancelledAt(subscription.getCancelledAt())
                .build();
    }
}

