package com.kitly.saas.billing.webhook;

import com.kitly.saas.entity.*;
import com.kitly.saas.entitlement.EntitlementService;
import com.kitly.saas.repository.SubscriptionRepository;
import com.kitly.saas.repository.TenantRepository;
import com.kitly.saas.repository.WebhookInboxRepository;
import com.kitly.saas.common.outbox.OutboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * Async processor for webhook events stored in webhook_inbox.
 * Processes pending webhooks on a schedule and updates subscription/entitlements.
 */
@Service
public class WebhookProcessor {
    
    private static final Logger logger = LoggerFactory.getLogger(WebhookProcessor.class);
    private static final Set<String> SUPPORTED_EVENTS = Set.of(
            "customer.subscription.created",
            "customer.subscription.updated",
            "customer.subscription.deleted",
            "invoice.payment_succeeded",
            "invoice.payment_failed"
    );
    
    private final WebhookInboxRepository webhookInboxRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final EntitlementService entitlementService;
    private final OutboxService outboxService;
    
    public WebhookProcessor(
            WebhookInboxRepository webhookInboxRepository,
            SubscriptionRepository subscriptionRepository,
            TenantRepository tenantRepository,
            EntitlementService entitlementService,
            OutboxService outboxService) {
        this.webhookInboxRepository = webhookInboxRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.entitlementService = entitlementService;
        this.outboxService = outboxService;
    }
    
    /**
     * Process pending webhooks every 5 seconds
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    public void processPendingWebhooks() {
        List<WebhookInbox> pendingWebhooks = webhookInboxRepository
                .findByProviderAndStatus("stripe", WebhookInbox.WebhookStatus.PENDING);
        
        if (pendingWebhooks.isEmpty()) {
            return;
        }
        
        logger.info("Processing {} pending webhooks", pendingWebhooks.size());
        
        for (WebhookInbox webhook : pendingWebhooks) {
            processWebhook(webhook);
        }
    }
    
    private void processWebhook(WebhookInbox webhook) {
        try {
            webhook.setStatus(WebhookInbox.WebhookStatus.PROCESSING);
            webhookInboxRepository.save(webhook);
            
            String eventType = webhook.getEventType();
            
            if (!SUPPORTED_EVENTS.contains(eventType)) {
                logger.info("Skipping unsupported event type: {}", eventType);
                webhook.setStatus(WebhookInbox.WebhookStatus.PROCESSED);
                webhook.setProcessedAt(LocalDateTime.now());
                webhookInboxRepository.save(webhook);
                return;
            }
            
            switch (eventType) {
                case "customer.subscription.created":
                case "customer.subscription.updated":
                    handleSubscriptionChange(webhook);
                    break;
                case "customer.subscription.deleted":
                    handleSubscriptionDeleted(webhook);
                    break;
                case "invoice.payment_succeeded":
                    handlePaymentSucceeded(webhook);
                    break;
                case "invoice.payment_failed":
                    handlePaymentFailed(webhook);
                    break;
            }
            
            webhook.setStatus(WebhookInbox.WebhookStatus.PROCESSED);
            webhook.setProcessedAt(LocalDateTime.now());
            webhookInboxRepository.save(webhook);
            
            logger.info("Successfully processed webhook: {}", webhook.getEventId());
            
        } catch (Exception e) {
            logger.error("Error processing webhook: {}", webhook.getEventId(), e);
            webhook.setStatus(WebhookInbox.WebhookStatus.FAILED);
            webhook.setErrorMessage(e.getMessage());
            webhook.setRetryCount(webhook.getRetryCount() + 1);
            webhookInboxRepository.save(webhook);
        }
    }
    
    @SuppressWarnings("unchecked")
    private void handleSubscriptionChange(WebhookInbox webhook) {
        Map<String, Object> data = (Map<String, Object>) webhook.getPayload().get("data");
        if (data == null) {
            throw new IllegalArgumentException("Missing data in webhook payload");
        }
        
        Map<String, Object> subscriptionData = (Map<String, Object>) data;
        
        // Extract subscription details
        String stripeSubscriptionId = (String) subscriptionData.get("id");
        String status = (String) subscriptionData.get("status");
        
        // For demo purposes, we'll use metadata to identify the tenant
        Map<String, Object> metadata = (Map<String, Object>) subscriptionData.get("metadata");
        String tenantIdStr = metadata != null ? (String) metadata.get("tenant_id") : null;
        
        if (tenantIdStr == null) {
            logger.warn("No tenant_id in subscription metadata, skipping");
            return;
        }
        
        UUID tenantId = UUID.fromString(tenantIdStr);
        Tenant tenant = tenantRepository.findById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));
        
        // Find or create subscription
        Optional<Subscription> existingSubscription = subscriptionRepository
                .findByTenantIdAndStatus(tenantId, Subscription.SubscriptionStatus.ACTIVE);
        
        Subscription subscription = existingSubscription.orElseGet(() -> {
            Subscription newSub = new Subscription();
            newSub.setTenant(tenant);
            newSub.setStartsAt(LocalDateTime.now());
            return newSub;
        });
        
        // Update subscription status
        subscription.setStatus(mapStripeStatus(status));
        
        // Extract plan details from metadata or items
        Map<String, Object> items = (Map<String, Object>) subscriptionData.get("items");
        if (items != null) {
            List<Map<String, Object>> dataItems = (List<Map<String, Object>>) items.get("data");
            if (dataItems != null && !dataItems.isEmpty()) {
                Map<String, Object> firstItem = dataItems.get(0);
                Map<String, Object> price = (Map<String, Object>) firstItem.get("price");
                if (price != null) {
                    Map<String, Object> priceMetadata = (Map<String, Object>) price.get("metadata");
                    if (priceMetadata != null && priceMetadata.containsKey("plan")) {
                        String planName = (String) priceMetadata.get("plan");
                        subscription.setPlan(mapPlanName(planName));
                    }
                }
            }
        }
        
        subscriptionRepository.save(subscription);
        
        // Recompute entitlements
        entitlementService.bumpEntitlementVersion(tenantId);
        
        // Publish outbox event
        Map<String, Object> eventPayload = new HashMap<>();
        eventPayload.put("tenantId", tenantId.toString());
        eventPayload.put("plan", subscription.getPlan().name());
        eventPayload.put("status", subscription.getStatus().name());
        
        outboxService.publish("EntitlementsChanged", "Tenant", tenantId, eventPayload);
        
        logger.info("Updated subscription for tenant: {}", tenantId);
    }
    
    private void handleSubscriptionDeleted(WebhookInbox webhook) {
        handleSubscriptionChange(webhook);
    }
    
    @SuppressWarnings("unchecked")
    private void handlePaymentSucceeded(WebhookInbox webhook) {
        Map<String, Object> data = (Map<String, Object>) webhook.getPayload().get("data");
        if (data == null) {
            return;
        }
        
        logger.info("Payment succeeded for invoice");
        // Payment succeeded - subscription should remain active
        // This is already handled by subscription.updated events
    }
    
    @SuppressWarnings("unchecked")
    private void handlePaymentFailed(WebhookInbox webhook) {
        Map<String, Object> data = (Map<String, Object>) webhook.getPayload().get("data");
        if (data == null) {
            return;
        }
        
        // Extract subscription ID from invoice
        String subscriptionId = (String) data.get("subscription");
        if (subscriptionId != null) {
            logger.warn("Payment failed for subscription: {}", subscriptionId);
            // In a real system, you'd update the subscription status to PAST_DUE
        }
    }
    
    private Subscription.SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus.toLowerCase()) {
            case "active" -> Subscription.SubscriptionStatus.ACTIVE;
            case "trialing" -> Subscription.SubscriptionStatus.TRIALING;
            case "canceled" -> Subscription.SubscriptionStatus.CANCELLED;
            case "past_due" -> Subscription.SubscriptionStatus.PAST_DUE;
            default -> Subscription.SubscriptionStatus.EXPIRED;
        };
    }
    
    private Subscription.SubscriptionPlan mapPlanName(String planName) {
        return switch (planName.toLowerCase()) {
            case "starter" -> Subscription.SubscriptionPlan.STARTER;
            case "professional", "pro" -> Subscription.SubscriptionPlan.PROFESSIONAL;
            case "enterprise" -> Subscription.SubscriptionPlan.ENTERPRISE;
            default -> Subscription.SubscriptionPlan.FREE;
        };
    }
}
