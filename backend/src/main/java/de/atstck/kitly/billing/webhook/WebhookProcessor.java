package de.atstck.kitly.billing.webhook;

import de.atstck.kitly.common.outbox.OutboxService;
import de.atstck.kitly.config.StripeConfig;
import de.atstck.kitly.entitlement.EntitlementService;
import de.atstck.kitly.entity.Invoice;
import de.atstck.kitly.entity.Subscription;
import de.atstck.kitly.entity.Tenant;
import de.atstck.kitly.entity.WebhookInbox;
import de.atstck.kitly.repository.InvoiceRepository;
import de.atstck.kitly.repository.SubscriptionRepository;
import de.atstck.kitly.repository.TenantRepository;
import de.atstck.kitly.repository.WebhookInboxRepository;
import de.atstck.kitly.service.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
            "invoice.payment_failed",
            "checkout.session.completed",
            "invoice.paid",
            "invoice.created",
            "payment_intent.succeeded",
            "payment_intent.created",
            "charge.succeeded",
            "payment_method.attached",
            "customer.created",
            "customer.updated",
            "invoice.finalized",
            "invoice.updated",
            "invoice_payment.paid"
    );

    private final WebhookInboxRepository webhookInboxRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;
    private final InvoiceRepository invoiceRepository;
    private final EntitlementService entitlementService;
    private final OutboxService outboxService;
    private final StripeConfig stripeConfig;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;

    // Custom exception for retry logic
    private static class RetryableWebhookException extends RuntimeException {
        public RetryableWebhookException(String message) {
            super(message);
        }
    }

    public WebhookProcessor(
            WebhookInboxRepository webhookInboxRepository,
            SubscriptionRepository subscriptionRepository,
            TenantRepository tenantRepository,
            InvoiceRepository invoiceRepository,
            EntitlementService entitlementService,
            OutboxService outboxService,
            StripeConfig stripeConfig,
            EmailService emailService,
            TransactionTemplate transactionTemplate) {
        this.webhookInboxRepository = webhookInboxRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.tenantRepository = tenantRepository;
        this.invoiceRepository = invoiceRepository;
        this.entitlementService = entitlementService;
        this.outboxService = outboxService;
        this.stripeConfig = stripeConfig;
        this.emailService = emailService;
        this.transactionTemplate = transactionTemplate;
    }

    /**
     * Process pending webhooks every 5 seconds
     */
    @Scheduled(fixedDelay = 5000)
    public void processPendingWebhooks() {
        // Use ordered query to process events in sequence
        List<WebhookInbox> pendingWebhooks = webhookInboxRepository
                .findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING);

        if (pendingWebhooks.isEmpty()) {
            return;
        }

        logger.info("Processing {} pending webhooks", pendingWebhooks.size());

        for (WebhookInbox webhook : pendingWebhooks) {
            try {
                // Process each webhook in its own transaction
                transactionTemplate.executeWithoutResult(status -> processWebhook(webhook));
            } catch (Exception e) {
                logger.error("Unexpected error executing webhook transaction for {}", webhook.getEventId(), e);
            }
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
                case "invoice.paid":
                    handlePaymentSucceeded(webhook);
                    break;
                case "invoice.payment_failed":
                    handlePaymentFailed(webhook);
                    break;
                case "checkout.session.completed":
                    handleCheckoutSessionCompleted(webhook);
                    break;
                case "invoice.finalized":
                    handleInvoiceFinalized(webhook);
                    break;
                case "invoice.created":
                case "payment_intent.succeeded":
                case "payment_intent.created":
                case "charge.succeeded":
                case "payment_method.attached":
                case "customer.created":
                case "customer.updated":
                case "invoice.updated":
                case "invoice_payment.paid":
                    // These events are part of the flow but we rely on other events for processing
                    logger.debug("Received event {}, no action required", eventType);
                    break;
            }

            webhook.setStatus(WebhookInbox.WebhookStatus.PROCESSED);
            webhook.setProcessedAt(LocalDateTime.now());
            webhookInboxRepository.save(webhook);

            logger.info("Successfully processed webhook: {}", webhook.getEventId());

        } catch (RetryableWebhookException e) {
            logger.info("Webhook {} requires retry: {}", webhook.getEventId(), e.getMessage());
            webhook.setRetryCount(webhook.getRetryCount() + 1);

            // Allow up to 12 retries (approx 1 minute with 5s delay)
            if (webhook.getRetryCount() > 12) {
                 webhook.setStatus(WebhookInbox.WebhookStatus.FAILED);
                 webhook.setErrorMessage("Max retries reached. Last error: " + e.getMessage());
            } else {
                 // Remain PENDING for next run
                 webhook.setStatus(WebhookInbox.WebhookStatus.PENDING);
                 logger.info("Setting status back to PENDING for webhook {} (attempt {})", webhook.getEventId(), webhook.getRetryCount());
            }
            webhookInboxRepository.save(webhook);

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

        Map<String, Object> subscriptionData = (Map<String, Object>) data.get("object");
        if (subscriptionData == null) {
            throw new IllegalArgumentException("Missing object in webhook data");
        }

        // Extract subscription details
        String stripeSubscriptionId = (String) subscriptionData.get("id");
        String status = (String) subscriptionData.get("status");
        String eventType = webhook.getEventType();

        // Extract event timestamp to ensure ordering
        long createdTimestamp = ((Number) subscriptionData.get("created")).longValue();
        LocalDateTime eventTime = LocalDateTime.ofEpochSecond(createdTimestamp, 0, ZoneOffset.UTC);

        // Safety check: If event is 'created' but we already have the subscription,
        // it means we processed a newer event already. We skip to avoid overwriting newer state.
        Optional<Subscription> byStripeId = subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId);

        if (byStripeId.isPresent()) {
            Subscription existing = byStripeId.get();
            // Compare timestamps: if stored event time is newer than this event, we skip properly
            if (existing.getLastStripeEventAt() != null && existing.getLastStripeEventAt().isAfter(eventTime)) {
                logger.info("Skipping outdated event for subscription {}. Event time: {}, Last processed: {}",
                        stripeSubscriptionId, eventTime, existing.getLastStripeEventAt());
                return;
            }
        }

        // Removed old 'created' check as the timestamp check covers it more robustly

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

        Subscription subscription;
        if (byStripeId.isPresent()) {
            subscription = byStripeId.get();
        } else {
             // If not found by Stripe ID, check if we have an existing ACTIVE or TRIALING subscription for this tenant
             // that we might want to replace (e.g. upgrade from internal plan or previous sub).
             // However, to be safe and avoid overwriting disjoint subscriptions, we should be careful.
             // Assuming 1 subscription per Tenant model:
            Optional<Subscription> existingSubscription = subscriptionRepository
                    .findByTenantIdAndStatus(tenantId, Subscription.SubscriptionStatus.ACTIVE);

            // Also check for trialing if no active found?
            if (existingSubscription.isEmpty()) {
                 existingSubscription = subscriptionRepository
                    .findByTenantIdAndStatus(tenantId, Subscription.SubscriptionStatus.TRIALING);
            }

            subscription = existingSubscription.orElseGet(() -> {
                Subscription newSub = new Subscription();
                newSub.setTenant(tenant);
                newSub.setStartsAt(LocalDateTime.now());
                return newSub;
            });

            if (subscription.getStripeSubscriptionId() != null && !subscription.getStripeSubscriptionId().equals(stripeSubscriptionId)) {
                logger.info("Replacing previous subscription ID {} with new ID {} for tenant {}",
                        subscription.getStripeSubscriptionId(), stripeSubscriptionId, tenantId);
            }
        }

        // Update subscription details
        subscription.setStripeSubscriptionId(stripeSubscriptionId);
        subscription.setStatus(mapStripeStatus(status));
        subscription.setLastStripeEventAt(eventTime);

        logger.info("Persisting subscription update for Tenant {}: StripeID={}, Status={}, EventTime={}",
                tenantId, stripeSubscriptionId, status, eventTime);

        // Extract plan details from metadata or items
        Map<String, Object> items = (Map<String, Object>) subscriptionData.get("items");
        if (items != null) {
            List<Map<String, Object>> dataItems = (List<Map<String, Object>>) items.get("data");
            if (dataItems != null && !dataItems.isEmpty()) {
                Map<String, Object> firstItem = dataItems.get(0);
                Map<String, Object> price = (Map<String, Object>) firstItem.get("price");
                if (price != null) {
                    boolean planSetByPriceId = false;

                    // Try to match by Price ID first
                    String priceId = (String) price.get("id");
                    if (priceId != null) {
                        String planName = stripeConfig.getPlanForPriceId(priceId);
                        if (planName != null) {
                            try {
                                subscription.setPlan(Subscription.SubscriptionPlan.valueOf(planName));
                                planSetByPriceId = true;
                            } catch (IllegalArgumentException e) {
                                logger.warn("Unknown plan name from price ID: {}", planName);
                            }
                        }
                    }

                    // Fallback to metadata if plan was not set by ID
                    if (!planSetByPriceId) {
                        Map<String, Object> priceMetadata = (Map<String, Object>) price.get("metadata");
                        if (priceMetadata != null && priceMetadata.containsKey("plan")) {
                            String planName = (String) priceMetadata.get("plan");
                            subscription.setPlan(mapPlanName(planName));
                        }
                    }
                }
            }
        }

        try {
            subscriptionRepository.save(subscription);
        } catch (DataIntegrityViolationException e) {
            // Check if it's a constraint violation on stripe_subscription_id
            logger.warn("Data integrity violation saving subscription (likely duplicate). Retrying fetch. Error: {}", e.getMessage());

            // Try to find the existing one that caused the conflict
            Optional<Subscription> duplicate = subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId);
            if (duplicate.isPresent()) {
                subscription = duplicate.get();
                // Re-apply updates to the found instance
                subscription.setStripeSubscriptionId(stripeSubscriptionId);
                subscription.setStatus(mapStripeStatus(status));
                // Plan update logic would need to run again or we trust the previous logic was same.
                // Simpler to just re-save this one.
                subscriptionRepository.save(subscription);
                logger.info("Recovered from duplicate subscription creation. Updated existing ID: {}", subscription.getId());
            } else {
                // If we still can't find it, rethrow
                throw e;
            }
        }

        // Recompute entitlements only if subscription is active or trialing
        if (subscription.getStatus() == Subscription.SubscriptionStatus.ACTIVE ||
                subscription.getStatus() == Subscription.SubscriptionStatus.TRIALING) {
            try {
                entitlementService.syncEntitlements(tenantId);
            } catch (Exception e) {
                logger.error("Failed to sync entitlements for tenant {}", tenantId, e);
            }
        } else {
            logger.info("Skipping entitlement sync for tenant {} because subscription status is {}", tenantId, subscription.getStatus());
        }

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
    private void handleCheckoutSessionCompleted(WebhookInbox webhook) {
        Map<String, Object> data = (Map<String, Object>) webhook.getPayload().get("data");
        if (data == null) {
            return;
        }

        Map<String, Object> sessionData = (Map<String, Object>) data.get("object");
        if (sessionData == null) {
            return;
        }

        // When a checkout session completes, we want to ensure the subscription is active
        // The subscription ID is available in the session object
        String subscriptionId = (String) sessionData.get("subscription");
        if (subscriptionId != null) {
            logger.info("Checkout session completed for subscription: {}", subscriptionId);

            // Sende Onboarding-E-Mail nach erfolgreichem Checkout
            Optional<Subscription> subscriptionOpt = subscriptionRepository.findFirstByStripeSubscriptionId(subscriptionId);
            if (subscriptionOpt.isPresent()) {
                Subscription subscription = subscriptionOpt.get();
                logger.info("Sending onboarding email for subscription: {}", subscriptionId);
                sendOnboardingEmail(subscription);
            } else {
                logger.warn("Subscription not found for checkout session: {}", subscriptionId);
                throw new RetryableWebhookException("Subscription not found for checkout session: " + subscriptionId);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void handlePaymentSucceeded(WebhookInbox webhook) {
        Map<String, Object> data = (Map<String, Object>) webhook.getPayload().get("data");
        if (data == null) {
            return;
        }

        Map<String, Object> invoiceData = (Map<String, Object>) data.get("object");
        if (invoiceData == null) {
            return;
        }

        String stripeInvoiceId = (String) invoiceData.get("id");

        // Find existing invoice or create new one
        Optional<Invoice> existingInvoice = invoiceRepository.findFirstByStripeInvoiceId(stripeInvoiceId);
        Invoice invoice;

        if (existingInvoice.isPresent()) {
            invoice = existingInvoice.get();
        } else {
            String stripeSubscriptionId = (String) invoiceData.get("subscription");
            if (stripeSubscriptionId == null) {
                logger.warn("Invoice {} has no subscription ID", stripeInvoiceId);
                return;
            }

            Optional<Subscription> subscriptionOpt = subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId);
            if (subscriptionOpt.isEmpty()) {
                logger.warn("Subscription not found for invoice: {}", stripeInvoiceId);
                throw new RetryableWebhookException("Subscription not found for invoice: " + stripeInvoiceId);
            }

            Subscription subscription = subscriptionOpt.get();
            invoice = new Invoice();
            invoice.setTenantId(subscription.getTenant().getId());
            invoice.setStripeInvoiceId(stripeInvoiceId);
        }

        // Update fields
        invoice.setInvoiceNumber((String) invoiceData.get("number"));
        invoice.setAmountDue(((Number) invoiceData.get("amount_due")).longValue());
        invoice.setAmountPaid(((Number) invoiceData.get("amount_paid")).longValue());
        invoice.setCurrency((String) invoiceData.get("currency"));
        invoice.setStatus((String) invoiceData.get("status"));
        invoice.setInvoicePdf((String) invoiceData.get("invoice_pdf"));
        invoice.setHostedInvoiceUrl((String) invoiceData.get("hosted_invoice_url"));

        // If email has not been sent yet, send it now (as paid invoice)
        if (!invoice.isEmailSent()) {
            sendInvoiceEmailImmediately(invoice, invoiceData);
            invoice.setEmailSent(true);
            invoice.setEmailScheduledAt(null); // Clear schedule as sent
        } else {
             // Maybe email was sent as "Open" already?
             // We could send a "Receipt" email here if we wanted to distinguish between Invoice and Receipt.
             // For now, let's assume one email per invoice is enough, or we can improve later.
             logger.info("Email for invoice {} already sent.", stripeInvoiceId);
        }

        invoiceRepository.save(invoice);
        logger.info("Saved invoice {} (PAID) for tenant {}", stripeInvoiceId, invoice.getTenantId());
    }

    @SuppressWarnings("unchecked")
    private void handlePaymentFailed(WebhookInbox webhook) {
        Map<String, Object> data = (Map<String, Object>) webhook.getPayload().get("data");
        if (data == null) {
            return;
        }

        Map<String, Object> invoiceData = (Map<String, Object>) data.get("object");
        if (invoiceData == null) {
            return;
        }

        // Extract subscription ID from invoice
        String subscriptionId = (String) invoiceData.get("subscription");
        if (subscriptionId != null) {
            logger.warn("Payment failed for subscription: {}", subscriptionId);
            // In a real system, you'd update the subscription status to PAST_DUE
        }
    }

    @SuppressWarnings("unchecked")
    private void handleInvoiceFinalized(WebhookInbox webhook) {
        Map<String, Object> data = (Map<String, Object>) webhook.getPayload().get("data");
        if (data == null) {
            return;
        }

        Map<String, Object> invoiceData = (Map<String, Object>) data.get("object");
        if (invoiceData == null) {
            return;
        }

        String stripeInvoiceId = (String) invoiceData.get("id");
        // Check if invoice already handled (e.g. by payment succeeded coming first)
         Optional<Invoice> existingInvoice = invoiceRepository.findFirstByStripeInvoiceId(stripeInvoiceId);
         if (existingInvoice.isPresent() && existingInvoice.get().isEmailSent()) {
             logger.info("Invoice {} already processed and email sent.", stripeInvoiceId);
             return;
         }

        // Find Subscription to get Tenant
        String stripeSubscriptionId = (String) invoiceData.get("subscription");
        if (stripeSubscriptionId == null) {
            logger.warn("Invoice {} has no subscription ID", stripeInvoiceId);
            return; // Cannot retry without ID
        }

        Optional<Subscription> subscriptionOpt = subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId);
        if (subscriptionOpt.isEmpty()) {
            // It might be a one-off invoice or usage, but if no sub found we can't link to tenant easily yet.
             logger.warn("Subscription not found for invoice: {}", stripeInvoiceId);
             throw new RetryableWebhookException("Subscription not found for invoice: " + stripeInvoiceId);
        }

        Subscription subscription = subscriptionOpt.get();

        Invoice invoice = existingInvoice.orElseGet(() -> {
             Invoice newInvoice = new Invoice();
             newInvoice.setTenantId(subscription.getTenant().getId());
             newInvoice.setStripeInvoiceId(stripeInvoiceId);
             return newInvoice;
        });

        invoice.setInvoiceNumber((String) invoiceData.get("number"));
        invoice.setAmountDue(((Number) invoiceData.get("amount_due")).longValue());
        invoice.setAmountPaid(((Number) invoiceData.get("amount_paid")).longValue()); // Likely 0 or partial
        // Use amount_due for the display amount usually, but amount_paid tracks what is paid.
        invoice.setCurrency((String) invoiceData.get("currency"));
        invoice.setStatus((String) invoiceData.get("status")); // e.g. open
        invoice.setInvoicePdf((String) invoiceData.get("invoice_pdf"));
        invoice.setHostedInvoiceUrl((String) invoiceData.get("hosted_invoice_url"));

        // Schedule email
        if (!invoice.isEmailSent()) {
            invoice.setEmailScheduledAt(LocalDateTime.now().plusMinutes(5));
            logger.info("Scheduled email for invoice {} at {}", stripeInvoiceId, invoice.getEmailScheduledAt());
        }

        invoiceRepository.save(invoice);
    }

    private void sendInvoiceEmailImmediately(Invoice invoice, Map<String, Object> invoiceData) {
        String invoiceNumber = (String) invoiceData.get("number");
        String invoicePdfUrl = (String) invoiceData.get("invoice_pdf");
        String customerEmail = (String) invoiceData.get("customer_email");
        String customerName = (String) invoiceData.get("customer_name");

        // amount_due is typically used for finalized invoices
        long amount = ((Number) invoiceData.get("amount_due")).longValue();
        String currency = (String) invoiceData.get("currency");
        long createdDate = ((Number) invoiceData.get("created")).longValue();

        String formattedAmount = String.format("%.2f", amount / 100.0);
        String formattedDate = java.time.format.DateTimeFormatter.ISO_LOCAL_DATE.format(
            java.time.LocalDateTime.ofEpochSecond(createdDate, 0, java.time.ZoneOffset.UTC)
        );

        try {
            if (customerEmail != null) {
                emailService.sendInvoiceEmail(
                    customerEmail,
                    customerName,
                    invoiceNumber,
                    invoicePdfUrl,
                    formattedAmount,
                    currency != null ? currency.toUpperCase() : "USD",
                    formattedDate
                );
                logger.info("Sent invoice {} to {}", invoiceNumber, customerEmail);
            } else {
                 // Try to find via subscription/tenant (re-fetch to be safe or use invoice.getTenantId)
                 // We have tenantId in invoice
                 Optional<Tenant> tenantOpt = tenantRepository.findById(invoice.getTenantId());
                 if (tenantOpt.isPresent() && tenantOpt.get().getOwner() != null) {
                      String ownerEmail = tenantOpt.get().getOwner().getEmail();
                      String ownerName = tenantOpt.get().getOwner().getUsername();

                      emailService.sendInvoiceEmail(
                            ownerEmail,
                            ownerName,
                            invoiceNumber,
                            invoicePdfUrl,
                            formattedAmount,
                            currency != null ? currency.toUpperCase() : "USD",
                            formattedDate
                        );
                        logger.info("Sent invoice {} to owner {}", invoiceNumber, ownerEmail);
                  } else {
                      logger.warn("Could not determine recipient for invoice {}", invoiceNumber);
                  }
            }
        } catch (Exception e) {
            logger.error("Failed to send invoice email for invoice {}", invoiceNumber, e);
            // We can't do much here inside a transaction except log it.
            // In a better system we would have an EmailJob queue.
            // Here, if it fails, emailSent remains false (because we only set it to true after this returns successfully in caller method if we move logic there, OR we suppress exception here).
            // Actually, suppressing exception means we mark it sent even if failed? No, caller sets it.
            // If I throw here, transaction rolls back?
            throw new RuntimeException("Failed to send invoice email", e);
        }
    }

    private Subscription.SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus.toLowerCase()) {
            case "active" -> Subscription.SubscriptionStatus.ACTIVE;
            case "trialing" -> Subscription.SubscriptionStatus.TRIALING;
            case "canceled" -> Subscription.SubscriptionStatus.CANCELLED;
            case "past_due" -> Subscription.SubscriptionStatus.PAST_DUE;
            case "incomplete", "incomplete_expired", "unpaid" -> Subscription.SubscriptionStatus.EXPIRED;
            default -> Subscription.SubscriptionStatus.EXPIRED;
        };
    }

    private Subscription.SubscriptionPlan mapPlanName(String planName) {
        return switch (planName.toLowerCase()) {
            case "starter" -> Subscription.SubscriptionPlan.STARTER;
            case "business" -> Subscription.SubscriptionPlan.BUSINESS;
            case "enterprise" -> Subscription.SubscriptionPlan.ENTERPRISE;
            default -> Subscription.SubscriptionPlan.FREE;
        };
    }

    /**
     * Sendet eine Onboarding-E-Mail an den Tenant-Owner nach erfolgreicher Zahlung
     */
    private void sendOnboardingEmail(Subscription subscription) {
        try {
            Tenant tenant = subscription.getTenant();
            if (tenant.getOwner() == null) {
                logger.warn("Tenant {} has no owner, cannot send onboarding email", tenant.getId());
                return;
            }

            String ownerEmail = tenant.getOwner().getEmail();
            String ownerUsername = tenant.getOwner().getUsername();
            String planName = subscription.getPlan() != null ? subscription.getPlan().name() : "Unknown";
            String ownerName = tenant.getOwner().getFirstName() != null ? tenant.getOwner().getFirstName() : ownerUsername;

            emailService.sendOnboardingEmail(ownerEmail, ownerName, ownerUsername, planName);
            logger.info("Onboarding email sent to {} for tenant {}", ownerEmail, tenant.getId());
        } catch (Exception e) {
            logger.error("Failed to send onboarding email for tenant {}", subscription.getTenant().getId(), e);
            // Wir werfen keine Exception, um die Webhook-Verarbeitung nicht zu unterbrechen
        }
    }
}
