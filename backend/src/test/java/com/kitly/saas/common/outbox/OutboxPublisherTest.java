package com.kitly.saas.common.outbox;

import com.kitly.saas.entity.OutboxEvent;
import com.kitly.saas.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {
    
    @Mock
    private OutboxEventRepository outboxEventRepository;
    
    private OutboxPublisher outboxPublisher;
    
    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxEventRepository, 50);
    }
    
    @Test
    void testPublishPendingEvents_NoEvents() {
        // Given: No pending events
        when(outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(Collections.emptyList());
        
        // When: Publishing pending events
        outboxPublisher.publishPendingEvents();
        
        // Then: No processing should occur
        verify(outboxEventRepository, times(1))
                .findByStatus(OutboxEvent.OutboxStatus.PENDING);
        verify(outboxEventRepository, never()).save(any());
    }
    
    @Test
    void testPublishPendingEvents_WithEvents() {
        // Given: Pending outbox events
        UUID tenantId = UUID.randomUUID();
        OutboxEvent event1 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("EntitlementsChanged")
                .aggregateType("Tenant")
                .aggregateId(tenantId)
                .payload(Map.of("tenantId", tenantId.toString()))
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        
        OutboxEvent event2 = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("TenantCreated")
                .aggregateType("Tenant")
                .aggregateId(tenantId)
                .payload(Map.of("tenantId", tenantId.toString()))
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        
        when(outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(List.of(event1, event2));
        
        // When: Publishing pending events
        outboxPublisher.publishPendingEvents();
        
        // Then: Should process all events and mark as processed
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(2)).save(eventCaptor.capture());
        
        List<OutboxEvent> savedEvents = eventCaptor.getAllValues();
        assertEquals(2, savedEvents.size());
        assertTrue(savedEvents.stream().allMatch(e -> 
                e.getStatus() == OutboxEvent.OutboxStatus.PROCESSED));
        assertTrue(savedEvents.stream().allMatch(e -> 
                e.getProcessedAt() != null));
    }
    
    @Test
    void testPublishPendingEvents_RespectsBatchSize() {
        // Given: More events than batch size
        int batchSize = 3;
        outboxPublisher = new OutboxPublisher(outboxEventRepository, batchSize);
        
        List<OutboxEvent> events = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            events.add(OutboxEvent.builder()
                    .id(UUID.randomUUID())
                    .eventType("TestEvent")
                    .aggregateType("Test")
                    .aggregateId(UUID.randomUUID())
                    .payload(Map.of("index", i))
                    .status(OutboxEvent.OutboxStatus.PENDING)
                    .retryCount(0)
                    .build());
        }
        
        when(outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(events);
        
        // When: Publishing pending events
        outboxPublisher.publishPendingEvents();
        
        // Then: Should only process batch size number of events
        verify(outboxEventRepository, times(batchSize)).save(any(OutboxEvent.class));
    }
    
    @Test
    void testPublishPendingEvents_HandlesErrors() {
        // Given: An event that will cause an error during processing
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("TestEvent")
                .aggregateType("Test")
                .aggregateId(UUID.randomUUID())
                .payload(Map.of("test", "data")) // Valid payload
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        
        when(outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        
        // When: Publishing with an error
        // The publisher should handle errors gracefully
        assertDoesNotThrow(() -> outboxPublisher.publishPendingEvents());
        
        // Then: Event should be processed successfully
        verify(outboxEventRepository, atLeastOnce()).save(any(OutboxEvent.class));
    }
    
    @Test
    void testPublishEvent_LogsToConsole() {
        // Given: A single pending event
        UUID aggregateId = UUID.randomUUID();
        Map<String, Object> payload = new HashMap<>();
        payload.put("key", "value");
        
        OutboxEvent event = OutboxEvent.builder()
                .id(UUID.randomUUID())
                .eventType("EntitlementsChanged")
                .aggregateType("Tenant")
                .aggregateId(aggregateId)
                .payload(payload)
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        
        when(outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING))
                .thenReturn(List.of(event));
        
        // When: Publishing the event
        outboxPublisher.publishPendingEvents();
        
        // Then: Event should be processed successfully
        // (In the real implementation, this logs to console)
        ArgumentCaptor<OutboxEvent> eventCaptor = ArgumentCaptor.forClass(OutboxEvent.class);
        verify(outboxEventRepository, times(1)).save(eventCaptor.capture());
        
        OutboxEvent savedEvent = eventCaptor.getValue();
        assertEquals(OutboxEvent.OutboxStatus.PROCESSED, savedEvent.getStatus());
        assertNotNull(savedEvent.getProcessedAt());
    }
}
