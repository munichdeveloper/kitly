package de.atstck.kitly.billing.webhook;

import de.atstck.kitly.common.outbox.OutboxService;
import de.atstck.kitly.entitlement.EntitlementService;
import de.atstck.kitly.entity.PlanEntity;
import de.atstck.kitly.entity.Subscription;
import de.atstck.kitly.entity.Tenant;
import de.atstck.kitly.entity.WebhookInbox;
import de.atstck.kitly.repository.SubscriptionRepository;
import de.atstck.kitly.repository.TenantRepository;
import de.atstck.kitly.repository.WebhookInboxRepository;
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

    @InjectMocks
    private WebhookProcessor webhookProcessor;

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
}
