package com.kitly.saas.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {
    
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private UUID id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionPlan plan = SubscriptionPlan.FREE;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private SubscriptionStatus status = SubscriptionStatus.ACTIVE;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle")
    private BillingCycle billingCycle;
    
    @Column(precision = 10, scale = 2)
    private BigDecimal amount;
    
    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";
    
    @Column(name = "starts_at", nullable = false)
    private LocalDateTime startsAt;
    
    @Column(name = "ends_at")
    private LocalDateTime endsAt;
    
    @Column(name = "trial_ends_at")
    private LocalDateTime trialEndsAt;
    
    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;
    
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "max_seats")
    private Integer maxSeats;
    
    @Version
    @Column(name = "entitlement_version")
    private Long entitlementVersion;
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (startsAt == null) {
            startsAt = LocalDateTime.now();
        }
        // Set default max seats based on plan
        if (maxSeats == null) {
            maxSeats = getDefaultMaxSeats(plan);
        }
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    public enum SubscriptionPlan {
        FREE,
        STARTER,
        PROFESSIONAL,
        ENTERPRISE
    }
    
    public enum SubscriptionStatus {
        ACTIVE,
        TRIALING,
        CANCELLED,
        EXPIRED,
        PAST_DUE
    }
    
    public enum BillingCycle {
        MONTHLY,
        YEARLY
    }
    
    private Integer getDefaultMaxSeats(SubscriptionPlan plan) {
        return switch (plan) {
            case FREE -> 3;
            case STARTER -> 10;
            case PROFESSIONAL -> 50;
            case ENTERPRISE -> null; // Unlimited
        };
    }
}
