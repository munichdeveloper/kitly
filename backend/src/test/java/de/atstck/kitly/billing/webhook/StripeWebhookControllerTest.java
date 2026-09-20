package de.atstck.kitly.billing.webhook;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.atstck.kitly.config.StripeConfig;
import de.atstck.kitly.entity.WebhookInbox;
import de.atstck.kitly.repository.WebhookInboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

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

    @Test
    void testValidSignature_StoresWebhookAndReturnsReceived() {
        // Given: A validly signed webhook payload for a supported event type
        String eventId = "evt_success_123";
        String payload = StripeWebhookFixtures.payloadFor("checkout.session.completed", eventId);
        String signature = StripeWebhookFixtures.signedHeader(payload, TEST_SECRET);

        when(webhookInboxRepository.findByProviderAndEventId("stripe", eventId))
                .thenReturn(Optional.empty());
        when(webhookInboxRepository.save(any(WebhookInbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Sending the webhook through the real controller
        ResponseEntity<Map<String, Object>> response = controller.handleStripeWebhook(payload, signature);

        // Then: The signature verifies and the event is persisted via the real controller
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("received", response.getBody().get("status"));
        assertEquals(eventId, response.getBody().get("eventId"));

        ArgumentCaptor<WebhookInbox> captor = ArgumentCaptor.forClass(WebhookInbox.class);
        verify(webhookInboxRepository, times(1)).save(captor.capture());
        WebhookInbox saved = captor.getValue();
        assertEquals("stripe", saved.getProvider());
        assertEquals(eventId, saved.getEventId());
        assertEquals("checkout.session.completed", saved.getEventType());
        assertEquals(WebhookInbox.WebhookStatus.PENDING, saved.getStatus());
    }

    @Test
    void testDuplicateEventId_IsIdempotent() {
        // Given: The same validly signed event, sent twice
        String eventId = "evt_idempotent_123";
        String payload = StripeWebhookFixtures.payloadFor("invoice.payment_succeeded", eventId);
        String signature = StripeWebhookFixtures.signedHeader(payload, TEST_SECRET);

        WebhookInbox stored = WebhookInbox.builder()
                .provider("stripe")
                .eventId(eventId)
                .eventType("invoice.payment_succeeded")
                .status(WebhookInbox.WebhookStatus.PENDING)
                .build();

        when(webhookInboxRepository.findByProviderAndEventId("stripe", eventId))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(stored));
        when(webhookInboxRepository.save(any(WebhookInbox.class)))
                .thenReturn(stored);

        // When: First delivery
        ResponseEntity<Map<String, Object>> first = controller.handleStripeWebhook(payload, signature);

        // Then: It is stored exactly once
        assertEquals(HttpStatus.OK, first.getStatusCode());
        assertEquals("received", first.getBody().get("status"));
        verify(webhookInboxRepository, times(1)).save(any(WebhookInbox.class));

        // When: Stripe retries the same event_id (e.g. because it never saw our 200)
        ResponseEntity<Map<String, Object>> second = controller.handleStripeWebhook(payload, signature);

        // Then: The duplicate is recognized and nothing new is saved
        assertEquals(HttpStatus.OK, second.getStatusCode());
        assertEquals("already_processed", second.getBody().get("status"));
        assertEquals(eventId, second.getBody().get("eventId"));
        verify(webhookInboxRepository, times(1)).save(any(WebhookInbox.class));
    }

    @ParameterizedTest(name = "signed webhook for event type \"{0}\" is accepted")
    @MethodSource("supportedEventTypes")
    void testAllSupportedEventTypes_AreAcceptedByRealController(String eventType) {
        // Given: A validly signed payload for each event type WebhookProcessor.SUPPORTED_EVENTS lists
        String payload = StripeWebhookFixtures.payloadFor(eventType);
        String signature = StripeWebhookFixtures.signedHeader(payload, TEST_SECRET);

        when(webhookInboxRepository.findByProviderAndEventId(eq("stripe"), any()))
                .thenReturn(Optional.empty());
        when(webhookInboxRepository.save(any(WebhookInbox.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // When: Sending it through the real controller
        ResponseEntity<Map<String, Object>> response = controller.handleStripeWebhook(payload, signature);

        // Then: Signature verification succeeds and the event is accepted
        assertEquals(HttpStatus.OK, response.getStatusCode(), "Event type " + eventType + " should be accepted");
        assertEquals("received", response.getBody().get("status"));
    }

    static Set<String> supportedEventTypes() {
        return WebhookProcessor.getSupportedEventTypes();
    }
}
