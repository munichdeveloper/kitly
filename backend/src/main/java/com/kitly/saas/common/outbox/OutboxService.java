package com.kitly.saas.common.outbox;

import com.kitly.saas.entity.OutboxEvent;
import com.kitly.saas.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service for managing outbox events using the outbox pattern.
 * Events are written to the outbox_events table within the same transaction
 * as the business logic, ensuring atomicity. This service also handles
 * processing, retrying, and cleanup of outbox events.
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
                .retryCount(0)
                .build();
        
        outboxEventRepository.save(event);
        
        logger.debug("Published outbox event: type={}, aggregateType={}, aggregateId={}", 
                eventType, aggregateType, aggregateId);
    }
    
    /**
     * Publish an event to the outbox (alternative method signature for compatibility).
     * 
     * @param aggregateType The type of aggregate
     * @param aggregateId The ID of the aggregate
     * @param eventType The type of event
     * @param payload The event payload
     * @return The saved OutboxEvent
     */
    @Transactional
    public OutboxEvent publishEvent(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        logger.info("Publishing event - aggregateType: {}, aggregateId: {}, eventType: {}", 
                aggregateType, aggregateId, eventType);
        
        OutboxEvent event = OutboxEvent.builder()
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .status(OutboxEvent.OutboxStatus.PENDING)
                .retryCount(0)
                .build();
        
        return outboxEventRepository.save(event);
    }
    
    /**
     * Process pending outbox events.
     * This is called by the scheduled task to process events in batches.
     */
    @Transactional
    public void processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING);
        logger.info("Processing {} pending outbox events", pendingEvents.size());
        
        for (OutboxEvent event : pendingEvents) {
            processEvent(event);
        }
    }
    
    /**
     * Process a single outbox event.
     * Marks event as processing, publishes to external system, then marks as processed.
     */
    @Transactional
    public void processEvent(OutboxEvent event) {
        try {
            event.setStatus(OutboxEvent.OutboxStatus.PROCESSING);
            outboxEventRepository.save(event);
            
            // Publish to external system (e.g., message queue, webhook endpoint)
            publishToExternalSystem(event);
            
            event.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
            event.setProcessedAt(LocalDateTime.now());
            outboxEventRepository.save(event);
            
            logger.info("Successfully processed outbox event: {}", event.getId());
        } catch (Exception e) {
            logger.error("Error processing outbox event: {}", event.getId(), e);
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            event.setErrorMessage(e.getMessage());
            event.setRetryCount(event.getRetryCount() + 1);
            outboxEventRepository.save(event);
        }
    }
    
    /**
     * Publish event to external system.
     * Currently logs the event, but can be extended to publish to:
     * - Message queue (RabbitMQ, Kafka)
     * - External webhook endpoint
     * - Analytics service
     * - Email service
     */
    private void publishToExternalSystem(OutboxEvent event) {
        logger.info("Publishing event to external system: {} - {}", event.getEventType(), event.getId());
        
        // Simulate successful processing
        // In production, implement actual publishing logic here
    }
    
    /**
     * Retry failed events that haven't exceeded max retries.
     * 
     * @param maxRetries Maximum number of retries allowed
     */
    @Transactional
    public void retryFailedEvents(int maxRetries) {
        List<OutboxEvent> failedEvents = outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.FAILED);
        logger.info("Retrying {} failed outbox events", failedEvents.size());
        
        for (OutboxEvent event : failedEvents) {
            if (event.getRetryCount() < maxRetries) {
                event.setStatus(OutboxEvent.OutboxStatus.PENDING);
                outboxEventRepository.save(event);
            }
        }
    }
    
    /**
     * Get event by ID.
     * 
     * @param id Event ID
     * @return Optional containing the event if found
     */
    public Optional<OutboxEvent> getEventById(UUID id) {
        return outboxEventRepository.findById(id);
    }
    
    /**
     * Get events by aggregate.
     * 
     * @param aggregateType Type of aggregate
     * @param aggregateId Aggregate ID
     * @return List of events for the aggregate
     */
    public List<OutboxEvent> getEventsByAggregate(String aggregateType, UUID aggregateId) {
        return outboxEventRepository.findByAggregateTypeAndAggregateId(aggregateType, aggregateId);
    }
    
    /**
     * Get events by status.
     * 
     * @param status Event status
     * @return List of events with the given status
     */
    public List<OutboxEvent> getEventsByStatus(OutboxEvent.OutboxStatus status) {
        return outboxEventRepository.findByStatus(status);
    }
    
    /**
     * Clean up old processed events.
     * 
     * @param daysToKeep Number of days to keep processed events
     */
    @Transactional
    public void cleanupOldEvents(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        List<OutboxEvent> processedEvents = outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PROCESSED);
        
        int deletedCount = 0;
        for (OutboxEvent event : processedEvents) {
            if (event.getProcessedAt() != null && event.getProcessedAt().isBefore(cutoffDate)) {
                outboxEventRepository.delete(event);
                deletedCount++;
            }
        }
        
        logger.info("Cleaned up {} old outbox events older than {} days", deletedCount, daysToKeep);
    }
}
