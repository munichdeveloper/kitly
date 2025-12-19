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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxServiceTest {
    
    @Mock
    private OutboxEventRepository outboxEventRepository;
    
    @InjectMocks
    private OutboxService outboxService;
    
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
}
