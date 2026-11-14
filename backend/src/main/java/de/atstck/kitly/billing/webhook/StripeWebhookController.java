package de.atstck.kitly.billing.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import de.atstck.kitly.config.StripeConfig;
import de.atstck.kitly.entity.WebhookInbox;
import de.atstck.kitly.repository.WebhookInboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
    private final StripeConfig stripeConfig;
    private final ObjectMapper objectMapper;

    public StripeWebhookController(
            WebhookInboxRepository webhookInboxRepository,
            StripeConfig stripeConfig,
            ObjectMapper objectMapper) {
        this.webhookInboxRepository = webhookInboxRepository;
        // Keep the config, not a copy of the secret: the secret must follow a stripe.mode switch at runtime.
        this.stripeConfig = stripeConfig;
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
            event = Webhook.constructEvent(payload, signatureHeader, stripeConfig.getWebhookSecret());
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

        // Reject events from the other Stripe mode (#49). They must never change subscription state.
        // We answer 200 (not 4xx) on purpose: the event can never become valid by being re-sent, and a
        // non-2xx answer would make Stripe retry it for days and eventually disable the endpoint, which
        // would also stop the legitimate events of that endpoint. The rejection is logged as a warning.
        String mode = stripeConfig.getCurrentMode();
        if (!livemodeMatches(event.getLivemode(), mode)) {
            logger.warn("Rejected Stripe webhook with mismatching livemode: id={}, type={}, livemode={}, stripe.mode={}",
                    event.getId(), event.getType(), event.getLivemode(), mode);
            return ResponseEntity.ok(Map.of(
                    "status", "ignored",
                    "reason", "livemode_mismatch",
                    "eventId", event.getId()
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

    /**
     * True when the event's {@code livemode} flag fits the configured mode. A missing flag is treated as
     * a mismatch: real Stripe events always carry it.
     */
    static boolean livemodeMatches(Boolean livemode, String mode) {
        if (livemode == null) {
            return false;
        }
        return livemode == StripeConfig.MODE_LIVE.equals(mode);
    }
}
