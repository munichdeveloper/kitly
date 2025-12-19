package com.kitly.saas.service;

import com.kitly.saas.entity.WebhookInbox;
import com.kitly.saas.repository.WebhookInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebhookServiceTest {
    
    @Mock
    private WebhookInboxRepository webhookInboxRepository;
    
    @InjectMocks
    private WebhookService webhookService;
    
    private Map<String, Object> testPayload;
    
    @BeforeEach
    void setUp() {
        testPayload = new HashMap<>();
        testPayload.put("id", "evt_test123");
        testPayload.put("type", "customer.subscription.created");
        testPayload.put("data", Map.of("object", Map.of("id", "sub_123")));
    }
    
    @Test
    void storeWebhook_ShouldCreateNewWebhook_WhenNotExists() {
        // Given
        String provider = "stripe";
        String eventId = "evt_test123";
        String eventType = "customer.subscription.created";
        
        when(webhookInboxRepository.findByProviderAndEventId(provider, eventId))
                .thenReturn(Optional.empty());
        when(webhookInboxRepository.save(any(WebhookInbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        WebhookInbox result = webhookService.storeWebhook(provider, eventId, eventType, testPayload);
        
        // Then
        assertNotNull(result);
        assertEquals(provider, result.getProvider());
        assertEquals(eventId, result.getEventId());
        assertEquals(eventType, result.getEventType());
        assertEquals(WebhookInbox.WebhookStatus.PENDING, result.getStatus());
        verify(webhookInboxRepository).save(any(WebhookInbox.class));
    }
    
    @Test
    void storeWebhook_ShouldReturnExistingWebhook_WhenAlreadyExists() {
        // Given
        String provider = "stripe";
        String eventId = "evt_test123";
        String eventType = "customer.subscription.created";
        
        WebhookInbox existing = WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider(provider)
                .eventId(eventId)
                .eventType(eventType)
                .status(WebhookInbox.WebhookStatus.PROCESSED)
                .build();
        
        when(webhookInboxRepository.findByProviderAndEventId(provider, eventId))
                .thenReturn(Optional.of(existing));
        
        // When
        WebhookInbox result = webhookService.storeWebhook(provider, eventId, eventType, testPayload);
        
        // Then
        assertEquals(existing, result);
        verify(webhookInboxRepository, never()).save(any(WebhookInbox.class));
    }
    
    @Test
    void processPendingWebhooks_ShouldProcessAllPendingWebhooks() {
        // Given
        WebhookInbox webhook1 = createTestWebhook("evt_1");
        WebhookInbox webhook2 = createTestWebhook("evt_2");
        
        when(webhookInboxRepository.findByStatus(WebhookInbox.WebhookStatus.PENDING))
                .thenReturn(Arrays.asList(webhook1, webhook2));
        when(webhookInboxRepository.save(any(WebhookInbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        webhookService.processPendingWebhooks();
        
        // Then
        verify(webhookInboxRepository).findByStatus(WebhookInbox.WebhookStatus.PENDING);
        verify(webhookInboxRepository, atLeast(4)).save(any(WebhookInbox.class));
    }
    
    @Test
    void processWebhook_ShouldMarkAsProcessed_WhenSuccessful() {
        // Given
        WebhookInbox webhook = createTestWebhook("evt_test");
        when(webhookInboxRepository.save(any(WebhookInbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        webhookService.processWebhook(webhook);
        
        // Then
        assertEquals(WebhookInbox.WebhookStatus.PROCESSED, webhook.getStatus());
        assertNotNull(webhook.getProcessedAt());
        verify(webhookInboxRepository, atLeast(2)).save(webhook);
    }
    
    @Test
    void retryFailedWebhooks_ShouldResetStatusToPending() {
        // Given
        WebhookInbox failedWebhook = createTestWebhook("evt_failed");
        failedWebhook.setStatus(WebhookInbox.WebhookStatus.FAILED);
        failedWebhook.setRetryCount(1);
        
        when(webhookInboxRepository.findByStatus(WebhookInbox.WebhookStatus.FAILED))
                .thenReturn(Collections.singletonList(failedWebhook));
        when(webhookInboxRepository.save(any(WebhookInbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        webhookService.retryFailedWebhooks(3);
        
        // Then
        assertEquals(WebhookInbox.WebhookStatus.PENDING, failedWebhook.getStatus());
        verify(webhookInboxRepository).save(failedWebhook);
    }
    
    @Test
    void retryFailedWebhooks_ShouldNotRetry_WhenMaxRetriesExceeded() {
        // Given
        WebhookInbox failedWebhook = createTestWebhook("evt_failed");
        failedWebhook.setStatus(WebhookInbox.WebhookStatus.FAILED);
        failedWebhook.setRetryCount(5);
        
        when(webhookInboxRepository.findByStatus(WebhookInbox.WebhookStatus.FAILED))
                .thenReturn(Collections.singletonList(failedWebhook));
        
        // When
        webhookService.retryFailedWebhooks(3);
        
        // Then
        assertEquals(WebhookInbox.WebhookStatus.FAILED, failedWebhook.getStatus());
        verify(webhookInboxRepository, never()).save(failedWebhook);
    }
    
    @Test
    void getWebhookById_ShouldReturnWebhook_WhenExists() {
        // Given
        UUID id = UUID.randomUUID();
        WebhookInbox webhook = createTestWebhook("evt_test");
        webhook.setId(id);
        
        when(webhookInboxRepository.findById(id)).thenReturn(Optional.of(webhook));
        
        // When
        Optional<WebhookInbox> result = webhookService.getWebhookById(id);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(webhook, result.get());
    }
    
    @Test
    void getWebhooksByProvider_ShouldReturnWebhooksList() {
        // Given
        String provider = "stripe";
        List<WebhookInbox> webhooks = Arrays.asList(
                createTestWebhook("evt_1"),
                createTestWebhook("evt_2")
        );
        
        when(webhookInboxRepository.findByProvider(provider)).thenReturn(webhooks);
        
        // When
        List<WebhookInbox> result = webhookService.getWebhooksByProvider(provider);
        
        // Then
        assertEquals(2, result.size());
        verify(webhookInboxRepository).findByProvider(provider);
    }
    
    private WebhookInbox createTestWebhook(String eventId) {
        return WebhookInbox.builder()
                .id(UUID.randomUUID())
                .provider("stripe")
                .eventId(eventId)
                .eventType("customer.subscription.created")
                .payload(testPayload)
                .status(WebhookInbox.WebhookStatus.PENDING)
                .retryCount(0)
                .build();
    }
}
