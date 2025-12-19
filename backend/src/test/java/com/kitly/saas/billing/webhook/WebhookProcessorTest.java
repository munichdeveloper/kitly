package com.kitly.saas.billing.webhook;

import com.kitly.saas.common.outbox.OutboxService;
import com.kitly.saas.entity.*;
import com.kitly.saas.entitlement.EntitlementService;
import com.kitly.saas.repository.SubscriptionRepository;
import com.kitly.saas.repository.TenantRepository;
import com.kitly.saas.repository.WebhookInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
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
    
    @InjectMocks
    private WebhookProcessor webhookProcessor;
    
    private UUID testTenantId;
    private Tenant testTenant;
    
    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testTenant = Tenant.builder()
                .id(testTenantId)
                .name("Test Tenant")
                .slug("test-tenant")
                .build();
    }
    
    @Test
    void testProcessPendingWebhooks_NoWebhooks() {
        // Given: No pending webhooks
        when(webhookInboxRepository.findByProviderAndStatus("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(Collections.emptyList());
        
        // When: Processing pending webhooks
        webhookProcessor.processPendingWebhooks();
        
        // Then: No processing should occur
        verify(webhookInboxRepository, times(1))
                .findByProviderAndStatus("stripe", WebhookInbox.WebhookStatus.PENDING);
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
        
        when(webhookInboxRepository.findByProviderAndStatus("stripe", WebhookInbox.WebhookStatus.PENDING))
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
        subscriptionData.put("metadata", Map.of("tenant_id", testTenantId.toString()));
        subscriptionData.put("items", Map.of("data", List.of(
                Map.of("price", Map.of("metadata", Map.of("plan", "starter")))
        )));
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", subscriptionData);
        
        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_test_123")
                .eventType("customer.subscription.created")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();
        
        when(webhookInboxRepository.findByProviderAndStatus("stripe", WebhookInbox.WebhookStatus.PENDING))
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
        verify(entitlementService, times(1)).bumpEntitlementVersion(testTenantId);
        verify(outboxService, times(1))
                .publish(eq("EntitlementsChanged"), eq("Tenant"), eq(testTenantId), any());
    }
    
    @Test
    void testProcessSubscriptionUpdated() {
        // Given: An existing subscription and an update webhook
        Subscription existingSubscription = Subscription.builder()
                .id(UUID.randomUUID())
                .tenant(testTenant)
                .plan(Subscription.SubscriptionPlan.STARTER)
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .build();
        
        Map<String, Object> subscriptionData = new HashMap<>();
        subscriptionData.put("id", "sub_test_123");
        subscriptionData.put("status", "active");
        subscriptionData.put("metadata", Map.of("tenant_id", testTenantId.toString()));
        subscriptionData.put("items", Map.of("data", List.of(
                Map.of("price", Map.of("metadata", Map.of("plan", "professional")))
        )));
        
        Map<String, Object> payload = new HashMap<>();
        payload.put("data", subscriptionData);
        
        WebhookInbox webhook = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId("evt_test_456")
                .eventType("customer.subscription.updated")
                .payload(payload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();
        
        when(webhookInboxRepository.findByProviderAndStatus("stripe", WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(List.of(webhook));
        when(tenantRepository.findById(testTenantId))
                .thenReturn(Optional.of(testTenant));
        when(subscriptionRepository.findByTenantIdAndStatus(testTenantId, Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(existingSubscription));
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Processing the webhook
        webhookProcessor.processPendingWebhooks();
        
        // Then: Should update subscription plan
        ArgumentCaptor<Subscription> subscriptionCaptor = ArgumentCaptor.forClass(Subscription.class);
        verify(subscriptionRepository, times(1)).save(subscriptionCaptor.capture());
        
        Subscription savedSubscription = subscriptionCaptor.getValue();
        assertEquals(Subscription.SubscriptionPlan.BUSINESS, savedSubscription.getPlan());
        
        verify(entitlementService, times(1)).bumpEntitlementVersion(testTenantId);
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
        
        when(webhookInboxRepository.findByProviderAndStatus("stripe", WebhookInbox.WebhookStatus.PENDING))
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
