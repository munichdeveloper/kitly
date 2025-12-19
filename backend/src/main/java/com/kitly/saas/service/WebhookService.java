package com.kitly.saas.service;

import com.kitly.saas.entity.WebhookInbox;
import com.kitly.saas.repository.WebhookInboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookService {
    
    private final WebhookInboxRepository webhookInboxRepository;
    
    /**
     * Store incoming webhook for idempotent processing
     */
    @Transactional
    public WebhookInbox storeWebhook(String provider, String eventId, String eventType, Map<String, Object> payload) {
        log.info("Storing webhook from provider: {}, eventId: {}, eventType: {}", provider, eventId, eventType);
        
        // Check if webhook already exists (idempotency)
        Optional<WebhookInbox> existing = webhookInboxRepository.findByProviderAndEventId(provider, eventId);
        if (existing.isPresent()) {
            log.info("Webhook already exists: {}", eventId);
            return existing.get();
        }
        
        WebhookInbox webhook = WebhookInbox.builder()
                .provider(provider)
                .eventId(eventId)
                .eventType(eventType)
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();
        
        return webhookInboxRepository.save(webhook);
    }
    
    /**
     * Process pending webhooks
     */
    @Transactional
    public void processPendingWebhooks() {
        List<WebhookInbox> pendingWebhooks = webhookInboxRepository.findByStatus(WebhookInbox.WebhookStatus.PENDING);
        log.info("Processing {} pending webhooks", pendingWebhooks.size());
        
        for (WebhookInbox webhook : pendingWebhooks) {
            processWebhook(webhook);
        }
    }
    
    /**
     * Process a single webhook
     */
    @Transactional
    public void processWebhook(WebhookInbox webhook) {
        try {
            webhook.setStatus(WebhookInbox.WebhookStatus.PROCESSING);
            webhookInboxRepository.save(webhook);
            
            // Process based on provider and event type
            switch (webhook.getProvider().toLowerCase()) {
                case "stripe":
                    processStripeWebhook(webhook);
                    break;
                default:
                    log.warn("Unknown webhook provider: {}", webhook.getProvider());
            }
            
            webhook.setStatus(WebhookInbox.WebhookStatus.PROCESSED);
            webhook.setProcessedAt(LocalDateTime.now());
            webhookInboxRepository.save(webhook);
            
            log.info("Successfully processed webhook: {}", webhook.getId());
        } catch (Exception e) {
            log.error("Error processing webhook: {}", webhook.getId(), e);
            webhook.setStatus(WebhookInbox.WebhookStatus.FAILED);
            webhook.setErrorMessage(e.getMessage());
            webhook.setRetryCount(webhook.getRetryCount() + 1);
            webhookInboxRepository.save(webhook);
        }
    }
    
    /**
     * Process Stripe-specific webhooks
     */
    private void processStripeWebhook(WebhookInbox webhook) {
        String eventType = webhook.getEventType();
        log.info("Processing Stripe webhook type: {}", eventType);
        
        // Handle different Stripe event types
        switch (eventType) {
            case "customer.subscription.created":
            case "customer.subscription.updated":
            case "customer.subscription.deleted":
                log.info("Subscription event: {}", eventType);
                // Handle subscription changes
                break;
            case "invoice.payment_succeeded":
            case "invoice.payment_failed":
                log.info("Invoice event: {}", eventType);
                // Handle payment events
                break;
            default:
                log.info("Unhandled Stripe event type: {}", eventType);
        }
    }
    
    /**
     * Retry failed webhooks
     */
    @Transactional
    public void retryFailedWebhooks(int maxRetries) {
        List<WebhookInbox> failedWebhooks = webhookInboxRepository.findByStatus(WebhookInbox.WebhookStatus.FAILED);
        log.info("Retrying {} failed webhooks", failedWebhooks.size());
        
        for (WebhookInbox webhook : failedWebhooks) {
            if (webhook.getRetryCount() < maxRetries) {
                webhook.setStatus(WebhookInbox.WebhookStatus.PENDING);
                webhookInboxRepository.save(webhook);
            }
        }
    }
    
    /**
     * Get webhook by ID
     */
    public Optional<WebhookInbox> getWebhookById(UUID id) {
        return webhookInboxRepository.findById(id);
    }
    
    /**
     * Get webhooks by provider
     */
    public List<WebhookInbox> getWebhooksByProvider(String provider) {
        return webhookInboxRepository.findByProvider(provider);
    }
    
    /**
     * Get webhooks by status
     */
    public List<WebhookInbox> getWebhooksByStatus(WebhookInbox.WebhookStatus status) {
        return webhookInboxRepository.findByStatus(status);
    }
}
