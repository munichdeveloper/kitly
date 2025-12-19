package com.kitly.saas.common.outbox;

import com.kitly.saas.entity.OutboxEvent;
import com.kitly.saas.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {
    
    @Mock
    private OutboxEventRepository outboxEventRepository;
    
    @InjectMocks
    private OutboxService outboxService;
    
    private UUID testAggregateId;
    private Map<String, Object> testPayload;
    
    @BeforeEach
    void setUp() {
        testAggregateId = UUID.randomUUID();
        testPayload = new HashMap<>();
        testPayload.put("key1", "value1");
        testPayload.put("key2", 123);
    }
    
    @Test
    void testPublish_CreatesOutboxEvent() {
        // Given: Event details
        String eventType = "EntitlementsChanged";
        String aggregateType = "Tenant";
        UUID aggregateId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", aggregateId.toString());
        payload.put("plan", "STARTER");
        
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        when(outboxEventRepository.save(eventCaptor.capture()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Publishing an event
        outboxService.publish(eventType, aggregateType, aggregateId, payload);
        
        // Then: Should save outbox event with correct details
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
        
        OutboxEvent savedEvent = eventCaptor.getValue();
        assertNotNull(savedEvent);
        assertEquals(eventType, savedEvent.getEventType());
        assertEquals(aggregateType, savedEvent.getAggregateType());
        assertEquals(aggregateId, savedEvent.getAggregateId());
        assertEquals(payload, savedEvent.getPayload());
        assertEquals(OutboxEvent.OutboxStatus.PENDING, savedEvent.getStatus());
    }
    
    @Test
    void publishEvent_ShouldCreateNewEvent() {
        // Given
        String aggregateType = "Subscription";
        String eventType = "SUBSCRIPTION_CREATED";
        
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        OutboxEvent result = outboxService.publishEvent(aggregateType, testAggregateId, eventType, testPayload);
        
        // Then
        assertNotNull(result);
        assertEquals(aggregateType, result.getAggregateType());
        assertEquals(testAggregateId, result.getAggregateId());
        assertEquals(eventType, result.getEventType());
        assertEquals(OutboxEvent.OutboxStatus.PENDING, result.getStatus());
        assertEquals(0, result.getRetryCount());
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }
    
    @Test
    void processPendingEvents_ShouldProcessAllPendingEvents() {
        // Given
        OutboxEvent event1 = createTestEvent("EVENT_1");
        OutboxEvent event2 = createTestEvent("EVENT_2");
        
        when(outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(Arrays.asList(event1, event2));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        outboxService.processPendingEvents();
        
        // Then
        verify(outboxEventRepository).findByStatus(OutboxEvent.OutboxStatus.PENDING);
        verify(outboxEventRepository, atLeast(4)).save(any(OutboxEvent.class));
    }
    
    @Test
    void processEvent_ShouldMarkAsProcessed_WhenSuccessful() {
        // Given
        OutboxEvent event = createTestEvent("TEST_EVENT");
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        outboxService.processEvent(event);
        
        // Then
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, event.getStatus());
        assertNotNull(event.getProcessedAt());
        verify(outboxEventRepository, atLeast(2)).save(event);
    }
    
    @Test
    void retryFailedEvents_ShouldResetStatusToPending() {
        // Given
        OutboxEvent failedEvent = createTestEvent("FAILED_EVENT");
        failedEvent.setStatus(OutboxEvent.OutboxStatus.FAILED);
        failedEvent.setRetryCount(1);
        
        when(outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.FAILED))
                .thenReturn(Collections.singletonList(failedEvent));
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When
        outboxService.retryFailedEvents(3);
        
        // Then
        assertEquals(OutboxEvent.OutboxStatus.PENDING, failedEvent.getStatus());
        verify(outboxEventRepository).save(failedEvent);
    }
    
    @Test
    void retryFailedEvents_ShouldNotRetry_WhenMaxRetriesExceeded() {
        // Given
        OutboxEvent failedEvent = createTestEvent("FAILED_EVENT");
        failedEvent.setStatus(OutboxEvent.OutboxStatus.FAILED);
        failedEvent.setRetryCount(5);
        
        when(outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.FAILED))
                .thenReturn(Collections.singletonList(failedEvent));
        
        // When
        outboxService.retryFailedEvents(3);
        
        // Then
        assertEquals(OutboxEvent.OutboxStatus.FAILED, failedEvent.getStatus());
        verify(outboxEventRepository, never()).save(failedEvent);
    }
    
    @Test
    void getEventById_ShouldReturnEvent_WhenExists() {
        // Given
        UUID eventId = UUID.randomUUID();
        OutboxEvent event = createTestEvent("TEST_EVENT");
        event.setId(eventId);
        
        when(outboxEventRepository.findById(eventId)).thenReturn(Optional.of(event));
        
        // When
        Optional<OutboxEvent> result = outboxService.getEventById(eventId);
        
        // Then
        assertTrue(result.isPresent());
        assertEquals(event, result.get());
    }
    
    @Test
    void getEventsByAggregate_ShouldReturnEventsList() {
        // Given
        String aggregateType = "Subscription";
        List<OutboxEvent> events = Arrays.asList(
                createTestEvent("EVENT_1"),
                createTestEvent("EVENT_2")
        );
        
        when(outboxEventRepository.findByAggregateTypeAndAggregateId(aggregateType, testAggregateId))
                .thenReturn(events);
        
        // When
        List<OutboxEvent> result = outboxService.getEventsByAggregate(aggregateType, testAggregateId);
        
        // Then
        assertEquals(2, result.size());
        verify(outboxEventRepository).findByAggregateTypeAndAggregateId(aggregateType, testAggregateId);
    }
    
    @Test
    void cleanupOldEvents_ShouldDeleteOldProcessedEvents() {
        // Given
        OutboxEvent oldEvent = createTestEvent("OLD_EVENT");
        oldEvent.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
        oldEvent.setProcessedAt(LocalDateTime.now().minusDays(60));
        
        OutboxEvent recentEvent = createTestEvent("RECENT_EVENT");
        recentEvent.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
        recentEvent.setProcessedAt(LocalDateTime.now().minusDays(10));
        
        when(outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PROCESSED))
                .thenReturn(Arrays.asList(oldEvent, recentEvent));
        
        // When
        outboxService.cleanupOldEvents(30);
        
        // Then
        verify(outboxEventRepository).delete(oldEvent);
        verify(outboxEventRepository, never()).delete(recentEvent);
    }
    
    @Test
    void cleanupOldEvents_ShouldNotDelete_WhenNoProcessedAtDate() {
        // Given
        OutboxEvent eventWithoutDate = createTestEvent("NO_DATE_EVENT");
        eventWithoutDate.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
        eventWithoutDate.setProcessedAt(null);
        
        when(outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PROCESSED))
                .thenReturn(Collections.singletonList(eventWithoutDate));
        
        // When
        outboxService.cleanupOldEvents(30);
        
        // Then
        verify(outboxEventRepository, never()).delete(eventWithoutDate);
    }
    
    @Test
    void testPublish_EntitlementsChangedEvent() {
        // Given: An EntitlementsChanged event
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("plan", "PROFESSIONAL");
        payload.put("status", "ACTIVE");
        
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Publishing the event
        outboxService.publish("EntitlementsChanged", "Tenant", tenantId, payload);
        
        // Then: Should be saved successfully
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }
    
    @Test
    void testPublish_MembershipChangedEvent() {
        // Given: A MembershipChanged event
        UUID membershipId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("membershipId", membershipId.toString());
        payload.put("action", "ADDED");
        payload.put("role", "MEMBER");
        
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Publishing the event
        outboxService.publish("MembershipChanged", "Membership", membershipId, payload);
        
        // Then: Should be saved successfully
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }
    
    @Test
    void testPublish_TenantCreatedEvent() {
        // Given: A TenantCreated event
        UUID tenantId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("tenantId", tenantId.toString());
        payload.put("name", "New Tenant");
        payload.put("slug", "new-tenant");
        
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Publishing the event
        outboxService.publish("TenantCreated", "Tenant", tenantId, payload);
        
        // Then: Should be saved successfully
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }
    
    @Test
    void testPublish_WithEmptyPayload() {
        // Given: An event with empty payload
        UUID aggregateId = UUID.randomUUID();
        Map<String, Object> emptyPayload = new HashMap<>();
        
        when(outboxEventRepository.save(any(OutboxEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        
        // When: Publishing the event
        outboxService.publish("TestEvent", "TestAggregate", aggregateId, emptyPayload);
        
        // Then: Should still be saved
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }
    
    private OutboxEvent createTestEvent(String eventType) {
        return OutboxEvent.builder()
                .id(UUID.randomUUID())
                .aggregateType("Subscription")
                .aggregateId(testAggregateId)
                .eventType(eventType)
                .payload(testPayload)
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .build();
    }
}
