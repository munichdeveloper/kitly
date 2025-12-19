package com.kitly.saas.service;

import com.kitly.saas.entity.OutboxEvent;
import com.kitly.saas.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OutboxService {
    
    private final OutboxEventRepository outboxEventRepository;
    
    /**
     * Publish an event to the outbox
     */
    @Transactional
    public OutboxEvent publishEvent(String aggregateType, UUID aggregateId, String eventType, Map<String, Object> payload) {
        log.info("Publishing event - aggregateType: {}, aggregateId: {}, eventType: {}", 
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
     * Process pending outbox events
     */
    @Transactional
    public void processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.PENDING);
        log.info("Processing {} pending outbox events", pendingEvents.size());
        
        for (OutboxEvent event : pendingEvents) {
            processEvent(event);
        }
    }
    
    /**
     * Process a single outbox event
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
            
            log.info("Successfully processed outbox event: {}", event.getId());
        } catch (Exception e) {
            log.error("Error processing outbox event: {}", event.getId(), e);
            event.setStatus(OutboxEvent.OutboxStatus.FAILED);
            event.setErrorMessage(e.getMessage());
            event.setRetryCount(event.getRetryCount() + 1);
            outboxEventRepository.save(event);
        }
    }
    
    /**
     * Publish event to external system
     */
    private void publishToExternalSystem(OutboxEvent event) {
        // This is where you would publish to:
        // - Message queue (RabbitMQ, Kafka)
        // - External webhook endpoint
        // - Analytics service
        // - Email service
        
        log.info("Publishing event to external system: {} - {}", event.getEventType(), event.getId());
        
        // Simulate successful processing
        // In production, implement actual publishing logic here
    }
    
    /**
     * Retry failed events
     */
    @Transactional
    public void retryFailedEvents(int maxRetries) {
        List<OutboxEvent> failedEvents = outboxEventRepository.findByStatus(OutboxEvent.OutboxStatus.FAILED);
        log.info("Retrying {} failed outbox events", failedEvents.size());
        
        for (OutboxEvent event : failedEvents) {
            if (event.getRetryCount() < maxRetries) {
                event.setStatus(OutboxEvent.OutboxStatus.PENDING);
                outboxEventRepository.save(event);
            }
        }
    }
    
    /**
     * Get event by ID
     */
    public Optional<OutboxEvent> getEventById(UUID id) {
        return outboxEventRepository.findById(id);
    }
    
    /**
     * Get events by aggregate
     */
    public List<OutboxEvent> getEventsByAggregate(String aggregateType, UUID aggregateId) {
        return outboxEventRepository.findByAggregateTypeAndAggregateId(aggregateType, aggregateId);
    }
    
    /**
     * Get events by status
     */
    public List<OutboxEvent> getEventsByStatus(OutboxEvent.OutboxStatus status) {
        return outboxEventRepository.findByStatus(status);
    }
    
    /**
     * Clean up old processed events
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
        
        log.info("Cleaned up {} old outbox events older than {} days", deletedCount, daysToKeep);
    }
}
