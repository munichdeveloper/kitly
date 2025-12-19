package com.kitly.saas.common.outbox;

import com.kitly.saas.entity.OutboxEvent;
import com.kitly.saas.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

/**
 * Service for publishing events using the outbox pattern.
 * Events are written to the outbox_events table within the same transaction
 * as the business logic, ensuring atomicity.
 */
@Service
public class OutboxService {
    
    private static final Logger logger = LoggerFactory.getLogger(OutboxService.class);
    
    private final OutboxEventRepository outboxEventRepository;
    
    public OutboxService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }
    
    /**
     * Publish an event to the outbox for eventual delivery.
     * This method should be called within a transaction with other business logic.
     * 
     * @param eventType The type of event (e.g., "EntitlementsChanged")
     * @param aggregateType The type of aggregate (e.g., "Tenant", "Membership")
     * @param aggregateId The ID of the aggregate
     * @param payload The event payload
     */
    @Transactional
    public void publish(String eventType, String aggregateType, UUID aggregateId, Map<String, Object> payload) {
        OutboxEvent event = OutboxEvent.builder()
                .eventType(eventType)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .payload(payload)
                .status(OutboxEvent.OutboxStatus.PENDING)
                .build();
        
        outboxEventRepository.save(event);
        
        logger.debug("Published outbox event: type={}, aggregateType={}, aggregateId={}", 
                eventType, aggregateType, aggregateId);
    }
}
