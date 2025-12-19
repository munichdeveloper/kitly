package com.kitly.saas.service;

import com.kitly.saas.common.exception.ResourceNotFoundException;
import com.kitly.saas.entity.Subscription;
import com.kitly.saas.entity.Tenant;
import com.kitly.saas.repository.SubscriptionRepository;
import com.kitly.saas.repository.TenantRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {
    
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final OutboxService outboxService;
    private final ApplicationEventPublisher eventPublisher;
    
    /**
     * Create a new subscription for a tenant
     */
    @Transactional
    public Subscription createSubscription(UUID tenantId, Subscription.SubscriptionPlan plan, 
                                          Subscription.BillingCycle billingCycle, Integer maxSeats) {
        log.info("Creating subscription for tenant: {}, plan: {}", tenantId, plan);
        
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new ResourceNotFoundException("Tenant not found"));
        
        // Check if active subscription exists
        subscriptionRepository.findByTenantIdAndStatus(tenantId, Subscription.SubscriptionStatus.ACTIVE)
                .ifPresent(existing -> {
                    throw new IllegalStateException("Active subscription already exists");
                });
        
        Subscription subscription = Subscription.builder()
                .tenant(tenant)
                .plan(plan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .billingCycle(billingCycle)
                .amount(calculateAmount(plan, billingCycle))
                .currency("USD")
                .startsAt(LocalDateTime.now())
                .maxSeats(maxSeats)
                .build();
        
        Subscription saved = subscriptionRepository.save(subscription);
        
        // Publish event to outbox
        publishSubscriptionEvent("SUBSCRIPTION_CREATED", saved);
        
        return saved;
    }
    
    /**
     * Update an existing subscription
     */
    @Transactional
    public Subscription updateSubscription(UUID subscriptionId, Subscription.SubscriptionPlan plan, 
                                          Subscription.BillingCycle billingCycle, Integer maxSeats) {
        log.info("Updating subscription: {}", subscriptionId);
        
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        
        subscription.setPlan(plan);
        subscription.setBillingCycle(billingCycle);
        subscription.setAmount(calculateAmount(plan, billingCycle));
        subscription.setMaxSeats(maxSeats);
        
        Subscription updated = subscriptionRepository.save(subscription);
        
        // Publish event to outbox
        publishSubscriptionEvent("SUBSCRIPTION_UPDATED", updated);
        
        return updated;
    }
    
    /**
     * Cancel a subscription
     */
    @Transactional
    public Subscription cancelSubscription(UUID subscriptionId) {
        log.info("Cancelling subscription: {}", subscriptionId);
        
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        
        subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        subscription.setCancelledAt(LocalDateTime.now());
        
        Subscription cancelled = subscriptionRepository.save(subscription);
        
        // Publish event to outbox
        publishSubscriptionEvent("SUBSCRIPTION_CANCELLED", cancelled);
        
        return cancelled;
    }
    
    /**
     * Get active subscription for tenant
     */
    public Subscription getActiveSubscription(UUID tenantId) {
        return subscriptionRepository.findByTenantIdAndStatus(tenantId, Subscription.SubscriptionStatus.ACTIVE)
                .orElseThrow(() -> new ResourceNotFoundException("No active subscription found"));
    }
    
    /**
     * Get all subscriptions for tenant
     */
    public List<Subscription> getSubscriptionsByTenant(UUID tenantId) {
        return subscriptionRepository.findByTenantId(tenantId);
    }
    
    /**
     * Get subscription by ID
     */
    public Subscription getSubscriptionById(UUID subscriptionId) {
        return subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
    }
    
    /**
     * Calculate subscription amount based on plan and billing cycle
     */
    private BigDecimal calculateAmount(Subscription.SubscriptionPlan plan, Subscription.BillingCycle billingCycle) {
        BigDecimal monthlyAmount = switch (plan) {
            case FREE -> BigDecimal.ZERO;
            case STARTER -> new BigDecimal("29.00");
            case PROFESSIONAL -> new BigDecimal("99.00");
            case ENTERPRISE -> new BigDecimal("299.00");
        };
        
        if (billingCycle == Subscription.BillingCycle.YEARLY) {
            // Apply 20% discount for yearly billing
            return monthlyAmount.multiply(new BigDecimal("12")).multiply(new BigDecimal("0.8"));
        }
        
        return monthlyAmount;
    }
    
    /**
     * Publish subscription event to outbox
     */
    private void publishSubscriptionEvent(String eventType, Subscription subscription) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("subscriptionId", subscription.getId().toString());
        payload.put("tenantId", subscription.getTenant().getId().toString());
        payload.put("plan", subscription.getPlan().toString());
        payload.put("status", subscription.getStatus().toString());
        payload.put("amount", subscription.getAmount() != null ? subscription.getAmount().toString() : null);
        payload.put("currency", subscription.getCurrency());
        
        outboxService.publishEvent("Subscription", subscription.getId(), eventType, payload);
    }
    
    /**
     * Handle webhook payment success
     */
    @Transactional
    public void handlePaymentSuccess(UUID subscriptionId) {
        log.info("Handling payment success for subscription: {}", subscriptionId);
        
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        
        if (subscription.getStatus() == Subscription.SubscriptionStatus.PAST_DUE) {
            subscription.setStatus(Subscription.SubscriptionStatus.ACTIVE);
            subscriptionRepository.save(subscription);
        }
        
        publishSubscriptionEvent("PAYMENT_SUCCEEDED", subscription);
    }
    
    /**
     * Handle webhook payment failure
     */
    @Transactional
    public void handlePaymentFailure(UUID subscriptionId) {
        log.info("Handling payment failure for subscription: {}", subscriptionId);
        
        Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new ResourceNotFoundException("Subscription not found"));
        
        subscription.setStatus(Subscription.SubscriptionStatus.PAST_DUE);
        subscriptionRepository.save(subscription);
        
        publishSubscriptionEvent("PAYMENT_FAILED", subscription);
    }
}
