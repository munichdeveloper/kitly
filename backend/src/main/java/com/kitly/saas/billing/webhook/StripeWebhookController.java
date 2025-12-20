package com.kitly.saas.billing.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitly.saas.entity.WebhookInbox;
import com.kitly.saas.repository.WebhookInboxRepository;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for receiving Stripe webhook events.
 * Verifies signature and stores events idempotently in webhook_inbox table.
 */
@RestController
@RequestMapping("/api/billing/webhooks")
public class StripeWebhookController {

    private static final Logger logger = LoggerFactory.getLogger(StripeWebhookController.class);
    private static final String PROVIDER = "stripe";

    private final WebhookInboxRepository webhookInboxRepository;
    private final String webhookSecret;
    private final ObjectMapper objectMapper;

    public StripeWebhookController(
            WebhookInboxRepository webhookInboxRepository,
            @Value("${stripe.webhook-secret}") String webhookSecret,
            ObjectMapper objectMapper) {
        this.webhookInboxRepository = webhookInboxRepository;
        this.webhookSecret = webhookSecret;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/stripe")
    public ResponseEntity<Map<String, Object>> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signatureHeader) {

        logger.info("Received Stripe webhook");

        // Verify signature
        Event event;
        try {
            event = Webhook.constructEvent(payload, signatureHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            logger.error("Invalid webhook signature", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid signature"
            ));
        } catch (Exception e) {
            logger.error("Error parsing webhook", e);
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid payload"
            ));
        }

        // Check for duplicate (idempotency)
        Optional<WebhookInbox> existing = webhookInboxRepository
                .findByProviderAndEventId(PROVIDER, event.getId());

        if (existing.isPresent()) {
            logger.info("Webhook already received: {}", event.getId());
            return ResponseEntity.ok(Map.of(
                    "status", "already_processed",
                    "eventId", event.getId()
            ));
        }

        // Store webhook for async processing
        try {
            // Parse payload to Map to avoid serialization issues with Stripe objects
            @SuppressWarnings("unchecked")
            Map<String, Object> payloadMap = objectMapper.readValue(payload, Map.class);

            WebhookInbox webhookInbox = WebhookInbox.builder()
                    .provider(PROVIDER)
                    .eventId(event.getId())
                    .eventType(event.getType())
                    .payload(payloadMap)
                    .status(WebhookInbox.WebhookStatus.PENDING)
                    .build();

            webhookInboxRepository.save(webhookInbox);

            logger.info("Stored webhook for processing: type={}, id={}",
                    event.getType(), event.getId());

            return ResponseEntity.ok(Map.of(
                    "status", "received",
                    "eventId", event.getId()
            ));

        } catch (Exception e) {
            logger.error("Error storing webhook", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "error", "Error storing webhook"
            ));
        }
    }
}
