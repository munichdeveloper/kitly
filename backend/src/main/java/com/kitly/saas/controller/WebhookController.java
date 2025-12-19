package com.kitly.saas.controller;

import com.kitly.saas.entity.WebhookInbox;
import com.kitly.saas.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {
    
    private final WebhookService webhookService;
    
    /**
     * Receive webhook from Stripe
     * This endpoint should be publicly accessible
     */
    @PostMapping("/stripe")
    public ResponseEntity<Map<String, String>> handleStripeWebhook(
            @RequestHeader(value = "Stripe-Signature", required = false) String signature,
            @RequestBody Map<String, Object> payload) {
        
        log.info("Received Stripe webhook");
        
        try {
            // Extract event details from Stripe payload
            String eventId = (String) payload.get("id");
            String eventType = (String) payload.get("type");
            
            if (eventId == null || eventType == null) {
                log.warn("Invalid webhook payload: missing id or type");
                return ResponseEntity.badRequest()
                        .body(Map.of("error", "Invalid webhook payload"));
            }
            
            // TODO: SECURITY - Verify Stripe signature in production
            // This is a critical security requirement to prevent unauthorized webhook processing
            // Implement signature verification using Stripe SDK:
            // 
            // String webhookSecret = System.getenv("STRIPE_WEBHOOK_SECRET");
            // try {
            //     Event event = Webhook.constructEvent(
            //         requestBody, signature, webhookSecret
            //     );
            // } catch (SignatureVerificationException e) {
            //     return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            //         .body(Map.of("error", "Invalid signature"));
            // }
            //
            // See: https://stripe.com/docs/webhooks/signatures
            
            // Store webhook for idempotent processing
            webhookService.storeWebhook("stripe", eventId, eventType, payload);
            
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            log.error("Error handling Stripe webhook", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process webhook"));
        }
    }
    
    /**
     * Generic webhook endpoint for other providers
     */
    @PostMapping("/{provider}")
    public ResponseEntity<Map<String, String>> handleGenericWebhook(
            @PathVariable String provider,
            @RequestBody Map<String, Object> payload) {
        
        log.info("Received webhook from provider: {}", provider);
        
        try {
            // Extract event details
            String eventId = (String) payload.getOrDefault("id", UUID.randomUUID().toString());
            String eventType = (String) payload.getOrDefault("type", "unknown");
            
            // Store webhook for processing
            webhookService.storeWebhook(provider, eventId, eventType, payload);
            
            return ResponseEntity.ok(Map.of("status", "received"));
        } catch (Exception e) {
            log.error("Error handling webhook from provider: {}", provider, e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to process webhook"));
        }
    }
    
    /**
     * Get webhook by ID (Admin only)
     */
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebhookInbox> getWebhook(@PathVariable UUID id) {
        return webhookService.getWebhookById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Get webhooks by provider (Admin only)
     */
    @GetMapping("/provider/{provider}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WebhookInbox>> getWebhooksByProvider(@PathVariable String provider) {
        List<WebhookInbox> webhooks = webhookService.getWebhooksByProvider(provider);
        return ResponseEntity.ok(webhooks);
    }
    
    /**
     * Get webhooks by status (Admin only)
     */
    @GetMapping("/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<WebhookInbox>> getWebhooksByStatus(@PathVariable String status) {
        try {
            WebhookInbox.WebhookStatus webhookStatus = WebhookInbox.WebhookStatus.valueOf(status.toUpperCase());
            List<WebhookInbox> webhooks = webhookService.getWebhooksByStatus(webhookStatus);
            return ResponseEntity.ok(webhooks);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
    
    /**
     * Manually trigger webhook processing (Admin only)
     */
    @PostMapping("/{id}/process")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> processWebhook(@PathVariable UUID id) {
        return webhookService.getWebhookById(id)
                .map(webhook -> {
                    webhookService.processWebhook(webhook);
                    return ResponseEntity.ok(Map.of("status", "processing"));
                })
                .orElse(ResponseEntity.notFound().build());
    }
    
    /**
     * Retry failed webhooks (Admin only)
     */
    @PostMapping("/retry-failed")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> retryFailedWebhooks(
            @RequestParam(defaultValue = "3") int maxRetries) {
        webhookService.retryFailedWebhooks(maxRetries);
        return ResponseEntity.ok(Map.of("status", "retrying"));
    }
}
