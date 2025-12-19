package com.kitly.saas.common.outbox;

import com.kitly.saas.entity.OutboxEvent;
import com.kitly.saas.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Scheduled job that publishes pending outbox events.
 * Initially logs events to console, but can be extended to publish to Kafka, RabbitMQ, or HTTP endpoints.
 */
@Component
@ConditionalOnProperty(name = "outbox.publisher.enabled", havingValue = "true", matchIfMissing = true)
public class OutboxPublisher {
    
    private static final Logger logger = LoggerFactory.getLogger(OutboxPublisher.class);
    
    private final OutboxEventRepository outboxEventRepository;
    private final int batchSize;
    
    public OutboxPublisher(
            OutboxEventRepository outboxEventRepository,
            @Value("${outbox.publisher.batch-size:50}") int batchSize) {
        this.outboxEventRepository = outboxEventRepository;
        this.batchSize = batchSize;
    }
    
    /**
     * Process pending outbox events in batches.
     * Runs every 10 seconds by default (configurable via outbox.publisher.schedule).
     */
    @Scheduled(cron = "${outbox.publisher.schedule:*/10 * * * * *}")
    @Transactional
    public void publishPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByStatus(OutboxEvent.OutboxStatus.PENDING);
        
        if (pendingEvents.isEmpty()) {
            return;
        }
        
        logger.info("Publishing {} pending outbox events", pendingEvents.size());
        
        // Process in batches
        int processed = 0;
        for (OutboxEvent event : pendingEvents) {
            if (processed >= batchSize) {
                break;
            }
            
            try {
                publishEvent(event);
                
                event.setStatus(OutboxEvent.OutboxStatus.PROCESSED);
                event.setProcessedAt(LocalDateTime.now());
                outboxEventRepository.save(event);
                
                processed++;
                
            } catch (Exception e) {
                logger.error("Error publishing outbox event: {}", event.getId(), e);
                event.setStatus(OutboxEvent.OutboxStatus.FAILED);
                event.setErrorMessage(e.getMessage());
                event.setRetryCount(event.getRetryCount() + 1);
                outboxEventRepository.save(event);
            }
        }
        
        logger.info("Successfully published {} outbox events", processed);
    }
    
    /**
     * Publish a single event.
     * Currently logs to console, but can be extended to:
     * - Publish to Kafka topic
     * - Send to RabbitMQ exchange
     * - POST to HTTP webhook endpoint
     * - Invoke AWS SNS/SQS
     */
    private void publishEvent(OutboxEvent event) {
        // For now, just log the event
        logger.info("OUTBOX EVENT [{}] - Aggregate: {}/{}, Payload: {}", 
                event.getEventType(),
                event.getAggregateType(),
                event.getAggregateId(),
                event.getPayload());
        
        // Future implementation examples:
        // 
        // Kafka:
        // kafkaTemplate.send("events." + event.getEventType(), event.getAggregateId().toString(), event.getPayload());
        //
        // RabbitMQ:
        // rabbitTemplate.convertAndSend("events.exchange", event.getEventType(), event.getPayload());
        //
        // HTTP:
        // restClient.post().uri(webhookUrl).body(event.getPayload()).retrieve();
    }
}
