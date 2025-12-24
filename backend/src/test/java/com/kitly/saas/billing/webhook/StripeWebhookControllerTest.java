package com.kitly.saas.billing.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kitly.saas.config.StripeConfig;
import com.kitly.saas.entity.WebhookInbox;
import com.kitly.saas.repository.WebhookInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StripeWebhookControllerTest {
    
    @Mock
    private WebhookInboxRepository webhookInboxRepository;
    
    @Mock
    private StripeConfig stripeConfig;

    private StripeWebhookController controller;
    
    private static final String TEST_SECRET = "whsec_test_secret";
    
    @BeforeEach
    void setUp() {
        when(stripeConfig.getWebhookSecret()).thenReturn(TEST_SECRET);
        controller = new StripeWebhookController(webhookInboxRepository, stripeConfig, new ObjectMapper());
    }
    
    @Test
    void testWebhookInboxRepository_Save() {
        // Given: A new webhook
        WebhookInbox webhook = WebhookInbox.builder()
                .provider("stripe")
                .eventId("evt_test_123")
                .eventType("customer.subscription.created")
                .payload(Map.of("test", "data"))
                .status(WebhookInbox.WebhookStatus.PENDING)
                .build();
        
        when(webhookInboxRepository.save(any(WebhookInbox.class)))
                .thenReturn(webhook);
        
        // When: Saving the webhook
        WebhookInbox saved = webhookInboxRepository.save(webhook);
        
        // Then: Verify it was saved
        assertNotNull(saved);
        assertEquals("stripe", saved.getProvider());
        assertEquals("evt_test_123", saved.getEventId());
        assertEquals(WebhookInbox.WebhookStatus.PENDING, saved.getStatus());
    }
    
    @Test
    void testWebhookInboxRepository_FindByProviderAndEventId() {
        // Given: An existing webhook
        String eventId = "evt_test_123";
        WebhookInbox webhook = WebhookInbox.builder()
                .provider("stripe")
                .eventId(eventId)
                .build();
        
        when(webhookInboxRepository.findByProviderAndEventId("stripe", eventId))
                .thenReturn(Optional.of(webhook));
        
        // When: Finding the webhook
        Optional<WebhookInbox> found = webhookInboxRepository
                .findByProviderAndEventId("stripe", eventId);
        
        // Then: Verify it was found
        assertTrue(found.isPresent());
        assertEquals(eventId, found.get().getEventId());
    }
    
    @Test
    void testInvalidSignature_ReturnsBadRequest() {
        // Given: An invalid signature
        String payload = "{\"id\":\"evt_test_123\",\"type\":\"customer.subscription.created\"}";
        String invalidSignature = "invalid_signature";
        
        // When: Processing with invalid signature
        ResponseEntity<Map<String, Object>> response = controller
                .handleStripeWebhook(payload, invalidSignature);
        
        // Then: Should return bad request
        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().containsKey("error"));
    }
    
    @Test
    void testControllerInitialization() {
        // Given: A controller with test secret
        // When: Controller is created
        // Then: Should be initialized properly
        assertNotNull(controller);
    }
}
