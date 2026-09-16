package de.atstck.kitly.billing.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Reusable test helper that generates valid, Stripe-signed webhook payloads
 * for every event type in {@link WebhookProcessor#getSupportedEventTypes()}.
 * <p>
 * This lets tests exercise the real {@link StripeWebhookController} (signature
 * verification via {@code Webhook.constructEvent}) and {@link WebhookProcessor}
 * without ever talking to the real Stripe API, in line with the Stripe
 * test-mode config seeded by {@code db/testdata/R__seed_stripe_test_mode.sql}.
 * <p>
 * The signing algorithm mirrors Stripe's own scheme (see
 * <a href="https://docs.stripe.com/webhooks#verify-manually">Stripe docs</a>):
 * {@code Stripe-Signature: t=<unix ts>,v1=hex(hmacSha256(secret, "<ts>.<payload>"))}.
 */
public final class StripeWebhookFixtures {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private StripeWebhookFixtures() {
    }

    /**
     * Builds a raw JSON payload (Stripe "event" envelope) for the given event
     * type, with a minimal but representative {@code data.object} so the
     * relevant {@link WebhookProcessor} handler can run against it.
     */
    public static String payloadFor(String eventType) {
        return payloadFor(eventType, "evt_" + UUID.randomUUID().toString().replace("-", ""));
    }

    public static String payloadFor(String eventType, String eventId) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", eventId);
        event.put("object", "event");
        event.put("type", eventType);
        event.put("created", Instant.now().getEpochSecond());
        event.put("data", Map.of("object", dataObjectFor(eventType)));

        try {
            return OBJECT_MAPPER.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize webhook fixture for " + eventType, e);
        }
    }

    /**
     * Signs a payload the same way Stripe does, producing a valid
     * {@code Stripe-Signature} header value for the given webhook secret.
     */
    public static String signedHeader(String payload, String webhookSecret) {
        return signedHeader(payload, webhookSecret, Instant.now());
    }

    public static String signedHeader(String payload, String webhookSecret, Instant timestamp) {
        long ts = timestamp.getEpochSecond();
        String signedPayload = ts + "." + payload;
        return "t=" + ts + ",v1=" + hmacSha256Hex(webhookSecret, signedPayload);
    }

    private static String hmacSha256Hex(String secret, String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to sign Stripe webhook fixture", e);
        }
    }

    /**
     * Minimal, representative {@code data.object} per supported event type.
     * Fields are chosen to cover what {@link WebhookProcessor} actually reads
     * for that event type; unrelated types get a generic placeholder object,
     * matching what Stripe itself would fan out for events Kitly doesn't act on.
     */
    private static Map<String, Object> dataObjectFor(String eventType) {
        long now = Instant.now().getEpochSecond();
        String tenantId = UUID.randomUUID().toString();

        return switch (eventType) {
            case "customer.subscription.created",
                 "customer.subscription.updated",
                 "customer.subscription.deleted" -> {
                Map<String, Object> price = new LinkedHashMap<>();
                price.put("id", "price_test_starter_ci");
                price.put("metadata", Map.of("plan", "starter"));

                Map<String, Object> item = Map.of("price", price);

                Map<String, Object> subscription = new LinkedHashMap<>();
                subscription.put("id", "sub_test_" + UUID.randomUUID());
                subscription.put("status", "active");
                subscription.put("created", now);
                subscription.put("metadata", Map.of("tenant_id", tenantId));
                subscription.put("items", Map.of("data", java.util.List.of(item)));
                yield subscription;
            }
            case "checkout.session.completed" -> {
                Map<String, Object> session = new LinkedHashMap<>();
                session.put("id", "cs_test_" + UUID.randomUUID());
                session.put("subscription", "sub_test_" + UUID.randomUUID());
                session.put("metadata", Map.of("tenant_id", tenantId));
                yield session;
            }
            case "invoice.payment_succeeded", "invoice.paid" -> invoiceObject("paid", true);
            case "invoice.payment_failed" -> invoiceObject("open", false);
            case "invoice.finalized" -> invoiceObject("open", false);
            case "invoice.created", "invoice.updated" -> invoiceObject("draft", false);
            case "payment_intent.succeeded", "payment_intent.created" -> {
                Map<String, Object> paymentIntent = new LinkedHashMap<>();
                paymentIntent.put("id", "pi_test_" + UUID.randomUUID());
                paymentIntent.put("status", eventType.endsWith("succeeded") ? "succeeded" : "requires_payment_method");
                paymentIntent.put("amount", 1999);
                paymentIntent.put("currency", "eur");
                yield paymentIntent;
            }
            case "charge.succeeded" -> {
                Map<String, Object> charge = new LinkedHashMap<>();
                charge.put("id", "ch_test_" + UUID.randomUUID());
                charge.put("amount", 1999);
                charge.put("currency", "eur");
                charge.put("paid", true);
                yield charge;
            }
            case "payment_method.attached" -> {
                Map<String, Object> paymentMethod = new LinkedHashMap<>();
                paymentMethod.put("id", "pm_test_" + UUID.randomUUID());
                paymentMethod.put("type", "card");
                yield paymentMethod;
            }
            case "customer.created", "customer.updated" -> {
                Map<String, Object> customer = new LinkedHashMap<>();
                customer.put("id", "cus_test_" + UUID.randomUUID());
                customer.put("email", "fixture@example.com");
                yield customer;
            }
            case "invoice_payment.paid" -> {
                Map<String, Object> invoicePayment = new LinkedHashMap<>();
                invoicePayment.put("id", "invpay_test_" + UUID.randomUUID());
                invoicePayment.put("invoice", "in_test_" + UUID.randomUUID());
                yield invoicePayment;
            }
            default -> Map.of("id", "obj_test_" + UUID.randomUUID());
        };
    }

    private static Map<String, Object> invoiceObject(String status, boolean paid) {
        Map<String, Object> invoice = new LinkedHashMap<>();
        invoice.put("id", "in_test_" + UUID.randomUUID());
        invoice.put("number", "INV-CI-" + System.nanoTime());
        invoice.put("subscription", "sub_test_" + UUID.randomUUID());
        invoice.put("amount_due", 1999L);
        invoice.put("amount_paid", paid ? 1999L : 0L);
        invoice.put("currency", "eur");
        invoice.put("status", status);
        invoice.put("invoice_pdf", "https://files.stripe.test/invoice.pdf");
        invoice.put("hosted_invoice_url", "https://invoice.stripe.test/i/test");
        invoice.put("customer_email", "fixture@example.com");
        invoice.put("customer_name", "Fixture Customer");
        invoice.put("created", Instant.now().getEpochSecond());
        return invoice;
    }
}
