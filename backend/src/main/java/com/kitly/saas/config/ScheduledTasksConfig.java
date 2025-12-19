package com.kitly.saas.config;

import com.kitly.saas.common.outbox.OutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration for scheduled tasks.
 * - WebhookProcessor handles its own webhook processing schedule
 * - OutboxPublisher handles event publishing schedule
 * - This config handles outbox retry and cleanup tasks
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "scheduling.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ScheduledTasksConfig {
    
    private final OutboxService outboxService;
    
    /**
     * Process pending outbox events every 30 seconds.
     * This is a fallback in addition to OutboxPublisher for redundancy.
     */
    @Scheduled(fixedDelay = 30000, initialDelay = 15000)
    public void processPendingOutboxEvents() {
        log.debug("Running scheduled task: processPendingOutboxEvents");
        try {
            outboxService.processPendingEvents();
        } catch (Exception e) {
            log.error("Error processing pending outbox events", e);
        }
    }
    
    /**
     * Retry failed outbox events every 5 minutes
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 90000)
    public void retryFailedOutboxEvents() {
        log.debug("Running scheduled task: retryFailedOutboxEvents");
        try {
            outboxService.retryFailedEvents(3);
        } catch (Exception e) {
            log.error("Error retrying failed outbox events", e);
        }
    }
    
    /**
     * Clean up old processed outbox events daily at 2 AM
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupOldOutboxEvents() {
        log.info("Running scheduled task: cleanupOldOutboxEvents");
        try {
            outboxService.cleanupOldEvents(30); // Keep events for 30 days
        } catch (Exception e) {
            log.error("Error cleaning up old outbox events", e);
        }
    }
}
