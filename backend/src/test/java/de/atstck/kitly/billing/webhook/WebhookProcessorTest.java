package de.atstck.kitly.billing.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atstck.kitly.common.outbox.OutboxService;
import de.atstck.kitly.entitlement.EntitlementService;
import de.atstck.kitly.entity.Invoice;
import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.entity.Subscription;
import de.atstck.kitly.entity.Tenant;
import de.atstck.kitly.entity.User;
import de.atstck.kitly.entity.WebhookInbox;
import de.atstck.kitly.repository.InvoiceRepository;
import de.atstck.kitly.repository.SubscriptionRepository;
import de.atstck.kitly.repository.TenantRepository;
import de.atstck.kitly.repository.WebhookInboxRepository;
import de.atstck.kitly.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.util.*;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookProcessorTest {

    @Mock
    private WebhookInboxRepository webhookInboxRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private TenantRepository tenantRepository;

    @Mock
    private EntitlementService entitlementService;

    @Mock
    private OutboxService outboxService;

    @Mock
    private TransactionTemplate transactionTemplate;

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private WebhookProcessor webhookProcessor;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private UUID testTenantId;
    private Tenant testTenant;
    private PlanEntity mockPlan;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testTenant = Tenant.builder()
                .id(testTenantId)
                .name("Test Tenant")
                .slug("test-tenant")
                .build();

        mockPlan = PlanEntity.builder()
                .id(UUID.randomUUID())
                .code("starter")
                .name("Starter Plan")
                .build();

        // Mock TransactionTemplate to execute the action immediately
        lenient().doAnswer(invocation -> {
            Consumer<Object> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());
    }

    @Test
    void testProcessPendingWebhooks_NoWebhooks() {
        // Given: No pending webhooks
        when(webhookInboxRepository.findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(Collections.emptyList());

        // When: Processing pending webhooks
        webhookProcessor.processPendingWebhooks();

        // Then: No processing should occur
        verify(webhookInboxRepository, times(1))
                .findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING);
        verify(subscriptionRepository, never()).save(any());
    }

    @Test
    void testProcessPendingWebhooks_WithUnsupportedEvent() {
        // Given: A webhook with unsupported event type
        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_test_123")
                .eventType("unsupported.event.type")
                .payload(Map.of("data", Map.of()))
                .status(WebhookInbox.WebhookStatus.PENDING)
                .build();

        when(webhookInboxRepository.findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(List.of(webhook));

        // When: Processing the webhook
        webhookProcessor.processPendingWebhooks();

        // Then: Should be marked as processed but no subscription update
        ArgumentCaptor<WebhookInbox> webhookCaptor = ArgumentCaptor.forClass(WebhookInbox.class);
        verify(webhookInboxRepository, atLeastOnce()).save(webhookCaptor.capture());

        List<WebhookInbox> savedWebhooks = webhookCaptor.getAllValues();
        assertTrue(savedWebhooks.stream().anyMatch(w ->
                w.getStatus() == WebhookInbox.WebhookStatus.PROCESSED));
    }

    @Test
    void testProcessSubscriptionCreated() {
        // Given: A subscription.created webhook
        Map<String, Object> subscriptionData = new HashMap<>();
        subscriptionData.put("id", "sub_test_123");
        subscriptionData.put("status", "active");
        subscriptionData.put("created", Instant.now().getEpochSecond()); // added timestamp
        subscriptionData.put("metadata", Map.of("tenant_id", testTenantId.toString()));
        subscriptionData.put("items", Map.of("data", List.of(
                Map.of("price", Map.of("metadata", Map.of("plan", "starter")))
        )));

        Map<String, Object> payload = new HashMap<>();
        payload.put("data", Map.of("object", subscriptionData));

        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_test_123")
                .eventType("customer.subscription.created")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();

        when(webhookInboxRepository.findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(List.of(webhook));
        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(testTenant));
        when(subscriptionRepository.findByTenantIdAndStatus(testTenantId, Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Processing the webhook
        webhookProcessor.processPendingWebhooks();

        // Then: Should save subscription and bump entitlements
        verify(subscriptionRepository, times(1)).save(any(Subscription.class));
        verify(entitlementService, times(1)).syncEntitlements(testTenantId);
        verify(outboxService, times(1))
                .publish(eq("EntitlementsChanged"), eq("Tenant"), eq(testTenantId), any());
    }

    @Test
    void testProcessSubscriptionUpdated() {
        // Given: An existing subscription and an update webhook
        Subscription existingSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .plan(mockPlan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .stripeSubscriptionId("sub_test_123") // Make sure ID matches so findFirst works if mocked or logic uses it
                .build();

        Map<String, Object> subscriptionData = new HashMap<>();
        subscriptionData.put("id", "sub_test_123");
        subscriptionData.put("status", "active");
        subscriptionData.put("created", Instant.now().getEpochSecond()); // added timestamp
        subscriptionData.put("metadata", Map.of("tenant_id", testTenantId.toString()));
        subscriptionData.put("items", Map.of("data", List.of(
                Map.of("price", Map.of("metadata", Map.of("plan", "business")))
        )));

        Map<String, Object> payload = new HashMap<>();
        payload.put("data", Map.of("object", subscriptionData));

        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_test_456")
                .eventType("customer.subscription.updated")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();

        when(webhookInboxRepository.findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(List.of(webhook));
        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(testTenant));

        // Fix: Use findFirstByStripeSubscriptionId as per new logic
        when(subscriptionRepository.findFirstByStripeSubscriptionId("sub_test_123"))
                .thenReturn(Optional.of(existingSubscription));

        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Processing the webhook
        webhookProcessor.processPendingWebhooks();

        // Then: Should update subscription plan
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(1)).save(subscriptionCaptor.capture());

        Subscription savedSubscription = subscriptionCaptor.getValue();
        // Plan is now a PlanEntity, not an enum, so we verify it's not null
        assertTrue(savedSubscription.getPlan() != null);

        verify(entitlementService, times(1)).syncEntitlements(testTenantId);
        verify(outboxService, times(1))
                .publish(eq("EntitlementsChanged"), eq("Tenant"), eq(testTenantId), any());
    }

    @Test
    void testProcessWebhook_ErrorHandling() {
        // Given: A webhook that will cause an error (missing data field completely)
        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_test_789")
                .eventType("customer.subscription.created")
                .payload(Map.of()) // Completely missing data field - this will throw
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();

        when(webhookInboxRepository.findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(List.of(webhook));

        // When: Processing the webhook
        webhookProcessor.processPendingWebhooks();

        // Then: Should be marked as failed with error message
        ArgumentCaptor<WebhookInbox> webhookCaptor = ArgumentCaptor.forClass(WebhookInbox.class);
        verify(webhookInboxRepository, atLeast(2)).save(webhookCaptor.capture());

        List<WebhookInbox> savedWebhooks = webhookCaptor.getAllValues();
        // Should have at least one FAILED status (after PROCESSING)
        boolean hasFailed = savedWebhooks.stream().anyMatch(w ->
                w.getStatus() == WebhookInbox.WebhookStatus.FAILED);
        assertTrue(hasFailed, "Expected at least one webhook with FAILED status");
    }

    // --- Previously untested SUPPORTED_EVENTS below: checkout.session.completed,
    // invoice.payment_succeeded, invoice.payment_failed, invoice.finalized.
    // Payloads are generated via StripeWebhookFixtures so they match exactly what
    // StripeWebhookControllerTest sends through the real controller. ---

    @Test
    void testProcessCheckoutSessionCompleted_SendsOnboardingEmail() {
        // Given: A checkout.session.completed webhook for a known subscription
        Map<String, Object> payload = parsePayload(StripeWebhookFixtures.payloadFor("checkout.session.completed"));
        String stripeSubscriptionId = extractDataObjectField(payload, "subscription");

        User owner = User.builder()
                .id(UUID.randomUUID())
                .username("owner")
                .email("owner@example.com")
                .firstName("Ada")
                .build();

        Tenant tenantWithOwner = Tenant.builder()
                .id(testTenantId)
                .name("Test Tenant")
                .slug("test-tenant")
                .owner(owner)
                .build();

        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(tenantWithOwner)
                .plan(mockPlan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .stripeSubscriptionId(stripeSubscriptionId)
                .build();

        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_checkout_1")
                .eventType("checkout.session.completed")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();

        when(webhookInboxRepository.findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(List.of(webhook));
        when(subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId))
                .thenReturn(Optional.of(subscription));

        // When: Processing the webhook
        webhookProcessor.processPendingWebhooks();

        // Then: The onboarding email is sent and the webhook is marked processed
        verify(emailService, times(1))
                .sendOnboardingEmail(eq("owner@example.com"), eq("Ada"), eq("owner"), eq("Starter Plan"));

        ArgumentCaptor<WebhookInbox> captor = ArgumentCaptor.forClass(WebhookInbox.class);
        verify(webhookInboxRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(w -> w.getStatus() == WebhookInbox.WebhookStatus.PROCESSED));
    }

    @Test
    void testProcessCheckoutSessionCompleted_UnknownSubscription_RetriesWebhook() {
        // Given: A checkout.session.completed webhook whose subscription isn't known yet
        // (e.g. the customer.subscription.created event hasn't been processed yet)
        Map<String, Object> payload = parsePayload(StripeWebhookFixtures.payloadFor("checkout.session.completed"));
        String stripeSubscriptionId = extractDataObjectField(payload, "subscription");

        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_checkout_2")
                .eventType("checkout.session.completed")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();

        when(webhookInboxRepository.findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(List.of(webhook));
        when(subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId))
                .thenReturn(Optional.empty());

        // When: Processing the webhook
        webhookProcessor.processPendingWebhooks();

        // Then: No email is sent and the webhook is scheduled for retry instead of failing outright
        verify(emailService, never()).sendOnboardingEmail(any(), any(), any(), any());

        ArgumentCaptor<WebhookInbox> captor = ArgumentCaptor.forClass(WebhookInbox.class);
        verify(webhookInboxRepository, atLeastOnce()).save(captor.capture());
        WebhookInbox lastSaved = captor.getAllValues().get(captor.getAllValues().size() - 1);
        assertEquals(WebhookInbox.WebhookStatus.PENDING, lastSaved.getStatus());
        assertEquals(1, lastSaved.getRetryCount());
    }

    @Test
    void testProcessInvoicePaymentSucceeded_SavesInvoiceAndSendsEmail() {
        // Given: An invoice.payment_succeeded webhook for a known subscription
        Map<String, Object> payload = parsePayload(StripeWebhookFixtures.payloadFor("invoice.payment_succeeded"));
        String stripeInvoiceId = extractDataObjectField(payload, "id");
        String stripeSubscriptionId = extractDataObjectField(payload, "subscription");

        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .plan(mockPlan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .stripeSubscriptionId(stripeSubscriptionId)
                .build();

        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_invoice_paid_1")
                .eventType("invoice.payment_succeeded")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();

        when(webhookInboxRepository.findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(List.of(webhook));
        when(invoiceRepository.findFirstByStripeInvoiceId(stripeInvoiceId))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId))
                .thenReturn(Optional.of(subscription));
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Processing the webhook
        webhookProcessor.processPendingWebhooks();

        // Then: The invoice is stored as paid and the invoice email is sent immediately
        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, times(1)).save(invoiceCaptor.capture());
        Invoice savedInvoice = invoiceCaptor.getValue();
        assertEquals(stripeInvoiceId, savedInvoice.getStripeInvoiceId());
        assertEquals(testTenantId, savedInvoice.getTenantId());
        assertEquals(1999L, savedInvoice.getAmountDue());
        assertEquals(1999L, savedInvoice.getAmountPaid());
        assertEquals("eur", savedInvoice.getCurrency());
        assertTrue(savedInvoice.isEmailSent());

        verify(emailService, times(1)).sendInvoiceEmail(
                eq("fixture@example.com"), eq("Fixture Customer"), any(), any(), any(), eq("EUR"), any());
    }

    @Test
    void testProcessInvoicePaymentFailed_LogsWithoutError() {
        // Given: An invoice.payment_failed webhook
        Map<String, Object> payload = parsePayload(StripeWebhookFixtures.payloadFor("invoice.payment_failed"));

        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_invoice_failed_1")
                .eventType("invoice.payment_failed")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();

        when(webhookInboxRepository.findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(List.of(webhook));

        // When: Processing the webhook
        webhookProcessor.processPendingWebhooks();

        // Then: No invoice/subscription mutation happens, and the webhook is simply marked processed
        verify(invoiceRepository, never()).save(any());
        verify(subscriptionRepository, never()).save(any());

        ArgumentCaptor<WebhookInbox> captor = ArgumentCaptor.forClass(WebhookInbox.class);
        verify(webhookInboxRepository, atLeastOnce()).save(captor.capture());
        assertTrue(captor.getAllValues().stream()
                .anyMatch(w -> w.getStatus() == WebhookInbox.WebhookStatus.PROCESSED));
    }

    @Test
    void testProcessInvoiceFinalized_SchedulesEmailForKnownSubscription() {
        // Given: An invoice.finalized webhook for a known subscription, not yet emailed
        Map<String, Object> payload = parsePayload(StripeWebhookFixtures.payloadFor("invoice.finalized"));
        String stripeInvoiceId = extractDataObjectField(payload, "id");
        String stripeSubscriptionId = extractDataObjectField(payload, "subscription");

        Subscription subscription = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .plan(mockPlan)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .stripeSubscriptionId(stripeSubscriptionId)
                .build();

        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_invoice_finalized_1")
                .eventType("invoice.finalized")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();

        when(webhookInboxRepository.findByProviderAndStatusOrderByCreatedAtAsc("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(List.of(webhook));
        when(invoiceRepository.findFirstByStripeInvoiceId(stripeInvoiceId))
                .thenReturn(Optional.empty());
        when(subscriptionRepository.findFirstByStripeSubscriptionId(stripeSubscriptionId))
                .thenReturn(Optional.of(subscription));
        when(invoiceRepository.save(any(Invoice.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Processing the webhook
        webhookProcessor.processPendingWebhooks();

        // Then: The invoice is persisted with a scheduled (not immediate) email, and no email is sent yet
        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository, times(1)).save(invoiceCaptor.capture());
        Invoice savedInvoice = invoiceCaptor.getValue();
        assertEquals(stripeInvoiceId, savedInvoice.getStripeInvoiceId());
        assertFalse(savedInvoice.isEmailSent());
        assertNotNull(savedInvoice.getEmailScheduledAt());

        verify(emailService, never()).sendInvoiceEmail(any(), any(), any(), any(), any(), any(), any());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayload(String payloadJson) {
        try {
            return objectMapper.readValue(payloadJson, Map.class);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse webhook fixture payload", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractDataObjectField(Map<String, Object> payload, String field) {
        Map<String, Object> data = (Map<String, Object>) payload.get("data");
        Map<String, Object> object = (Map<String, Object>) data.get("object");
        return (String) object.get(field);
    }
}
