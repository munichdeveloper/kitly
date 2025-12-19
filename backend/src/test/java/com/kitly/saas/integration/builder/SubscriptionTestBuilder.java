package com.kitly.saas.integration.builder;

import com.kitly.saas.entity.Subscription;
import com.kitly.saas.entity.Tenant;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Builder for Subscription test data
 */
public class SubscriptionTestBuilder {
    
    private Tenant tenant;
    private Subscription.SubscriptionPlan plan = Subscription.SubscriptionPlan.FREE;
    private Subscription.SubscriptionStatus status = Subscription.SubscriptionStatus.TRIALING;
    private Subscription.BillingCycle billingCycle;
    private BigDecimal amount;
    private String currency = "USD";
    private LocalDateTime startsAt = LocalDateTime.now();
    private LocalDateTime endsAt;
    private LocalDateTime trialEndsAt = LocalDateTime.now().plusDays(14);
    private Integer maxSeats = 3;
    
    public static SubscriptionTestBuilder aSubscription() {
        return new SubscriptionTestBuilder();
    }
    
    public SubscriptionTestBuilder withTenant(Tenant tenant) {
        this.tenant = tenant;
        return this;
    }
    
    public SubscriptionTestBuilder withPlan(Subscription.SubscriptionPlan plan) {
        this.plan = plan;
        return this;
    }
    
    public SubscriptionTestBuilder withStatus(Subscription.SubscriptionStatus status) {
        this.status = status;
        return this;
    }
    
    public SubscriptionTestBuilder withBillingCycle(Subscription.BillingCycle billingCycle) {
        this.billingCycle = billingCycle;
        return this;
    }
    
    public SubscriptionTestBuilder withAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }
    
    public SubscriptionTestBuilder withCurrency(String currency) {
        this.currency = currency;
        return this;
    }
    
    public SubscriptionTestBuilder withStartsAt(LocalDateTime startsAt) {
        this.startsAt = startsAt;
        return this;
    }
    
    public SubscriptionTestBuilder withEndsAt(LocalDateTime endsAt) {
        this.endsAt = endsAt;
        return this;
    }
    
    public SubscriptionTestBuilder withTrialEndsAt(LocalDateTime trialEndsAt) {
        this.trialEndsAt = trialEndsAt;
        return this;
    }
    
    public SubscriptionTestBuilder withMaxSeats(Integer maxSeats) {
        this.maxSeats = maxSeats;
        return this;
    }
    
    public Subscription build() {
        return Subscription.builder()
                .tenant(tenant)
                .plan(plan)
                .status(status)
                .billingCycle(billingCycle)
                .amount(amount)
                .currency(currency)
                .startsAt(startsAt)
                .endsAt(endsAt)
                .trialEndsAt(trialEndsAt)
                .maxSeats(maxSeats)
                .build();
    }
}
