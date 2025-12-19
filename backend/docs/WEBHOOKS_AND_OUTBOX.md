# Webhook & Outbox Pattern Documentation

## Overview

Kitly implements the Webhook Inbox and Outbox patterns for reliable event-driven architecture. These patterns ensure:
- **Idempotency**: Webhook events are processed exactly once
- **Reliability**: Failed events are automatically retried
- **Decoupling**: External system failures don't block the main application flow
- **Auditability**: Complete history of all events is maintained

## Architecture

### Webhook Inbox Pattern

The Webhook Inbox pattern receives and processes incoming webhooks from external systems (e.g., Stripe, payment providers).

#### Components

1. **WebhookInbox Entity** - Stores incoming webhook events
   - `provider`: Source of the webhook (e.g., "stripe")
   - `eventId`: Unique identifier from the provider
   - `eventType`: Type of event (e.g., "customer.subscription.created")
   - `payload`: Complete webhook payload as JSON
   - `status`: Processing status (PENDING, PROCESSING, PROCESSED, FAILED)
   - `retryCount`: Number of processing attempts

2. **WebhookService** - Business logic for webhook processing
   - Idempotent storage (duplicate detection)
   - Event processing based on provider and type
   - Automatic retry logic for failed events

3. **WebhookController** - REST endpoints for receiving webhooks
   - Public endpoints for webhook reception
   - Admin endpoints for monitoring and manual processing

#### Flow

```
External System → POST /api/webhooks/{provider}
                     ↓
              Store in webhook_inbox
                     ↓
              Scheduled processor
                     ↓
              Process webhook
                     ↓
              Mark as PROCESSED
```

### Outbox Pattern

The Outbox pattern publishes events to external systems reliably, ensuring no events are lost even if external systems are unavailable.

#### Components

1. **OutboxEvent Entity** - Stores outgoing events
   - `aggregateType`: Type of entity (e.g., "Subscription")
   - `aggregateId`: ID of the entity
   - `eventType`: Type of event (e.g., "SUBSCRIPTION_CREATED")
   - `payload`: Event data as JSON
   - `status`: Processing status (PENDING, PROCESSING, PROCESSED, FAILED)
   - `retryCount`: Number of publishing attempts

2. **OutboxService** - Business logic for event publishing
   - Event creation and storage
   - Publishing to external systems
   - Automatic retry logic for failed events
   - Old event cleanup

3. **BillingService** - Publishes subscription events
   - Creates outbox events for subscription changes
   - Events: SUBSCRIPTION_CREATED, SUBSCRIPTION_UPDATED, etc.

#### Flow

```
Business Operation → Save to Database
                          ↓
                   Publish to outbox_events
                          ↓
                   Scheduled processor
                          ↓
                   Publish to external system
                          ↓
                   Mark as PROCESSED
```

## Database Schema

### webhook_inbox

```sql
CREATE TABLE webhook_inbox (
    id UUID PRIMARY KEY,
    provider VARCHAR(50) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP,
    UNIQUE(provider, event_id)
);
```

### outbox_events

```sql
CREATE TABLE outbox_events (
    id UUID PRIMARY KEY,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id UUID NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL,
    processed_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER DEFAULT 0,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP
);
```

## API Endpoints

### Webhook Reception

#### POST /api/webhooks/stripe
Receive webhooks from Stripe (public endpoint).

**Request:**
```json
{
  "id": "evt_1234567890",
  "type": "customer.subscription.created",
  "data": {
    "object": {
      "id": "sub_1234567890",
      "customer": "cus_1234567890",
      "status": "active"
    }
  }
}
```

**Response:**
```json
{
  "status": "received"
}
```

#### POST /api/webhooks/{provider}
Generic webhook endpoint for other providers.

### Admin Endpoints (Require ADMIN role)

#### GET /api/webhooks/{id}
Get webhook by ID.

#### GET /api/webhooks/provider/{provider}
Get all webhooks from a provider.

#### GET /api/webhooks/status/{status}
Get webhooks by status (PENDING, PROCESSING, PROCESSED, FAILED).

#### POST /api/webhooks/{id}/process
Manually trigger webhook processing.

#### POST /api/webhooks/retry-failed
Retry all failed webhooks.

## Scheduled Tasks

The system includes automated background jobs:

1. **Process Pending Webhooks** - Every 30 seconds
   - Processes webhooks in PENDING status
   
2. **Process Pending Outbox Events** - Every 30 seconds
   - Publishes events in PENDING status

3. **Retry Failed Webhooks** - Every 5 minutes
   - Retries webhooks in FAILED status (max 3 attempts)

4. **Retry Failed Outbox Events** - Every 5 minutes
   - Retries events in FAILED status (max 3 attempts)

5. **Cleanup Old Events** - Daily at 2 AM
   - Removes processed events older than 30 days

## Configuration

Scheduled tasks can be disabled via application properties:

```yaml
scheduling:
  enabled: true  # Set to false to disable all scheduled tasks
```

## Usage Examples

### Receiving a Webhook

Webhooks are automatically stored when received. The system ensures idempotency by checking for duplicate `event_id` per `provider`.

### Publishing an Event

```java
@Autowired
private OutboxService outboxService;

public void createSubscription(Subscription subscription) {
    // Save subscription to database
    subscriptionRepository.save(subscription);
    
    // Publish event to outbox
    Map<String, Object> payload = new HashMap<>();
    payload.put("subscriptionId", subscription.getId().toString());
    payload.put("tenantId", subscription.getTenant().getId().toString());
    payload.put("plan", subscription.getPlan().toString());
    
    outboxService.publishEvent(
        "Subscription",
        subscription.getId(),
        "SUBSCRIPTION_CREATED",
        payload
    );
}
```

### Processing a Custom Webhook

To add support for a new webhook provider, extend `WebhookService.processWebhook()`:

```java
private void processCustomWebhook(WebhookInbox webhook) {
    String eventType = webhook.getEventType();
    
    switch (eventType) {
        case "custom.event.type":
            // Handle custom event
            break;
        default:
            log.info("Unhandled event type: {}", eventType);
    }
}
```

## Security Considerations

### Webhook Verification

In production, always verify webhook signatures:

```java
@PostMapping("/stripe")
public ResponseEntity<Map<String, String>> handleStripeWebhook(
        @RequestHeader("Stripe-Signature") String signature,
        @RequestBody String payload) {
    
    // Verify signature
    if (!verifyStripeSignature(signature, payload)) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
    }
    
    // Process webhook...
}
```

### Access Control

- Webhook reception endpoints are public (no authentication required)
- Admin endpoints require ADMIN role
- Webhook processing is isolated in background threads

### Data Privacy

- Webhook payloads may contain sensitive data
- Ensure proper database encryption
- Implement data retention policies

## Monitoring

Monitor the following metrics:

1. **Webhook Processing Rate**
   - Number of webhooks processed per minute
   - Average processing time

2. **Failed Events**
   - Count of webhooks/events in FAILED status
   - Failed event types and error messages

3. **Retry Patterns**
   - Average retry count before success
   - Events exceeding max retries

4. **Queue Depth**
   - Number of PENDING webhooks/events
   - Processing lag time

## Troubleshooting

### Webhooks Not Processing

1. Check that scheduled tasks are enabled
2. Verify webhook is in PENDING status
3. Check application logs for errors
4. Manually trigger processing via admin endpoint

### Failed Webhooks

1. Check error_message in database
2. Verify external system availability
3. Check webhook payload format
4. Manually retry via admin endpoint

### Duplicate Events

The system prevents duplicates via unique constraint on `(provider, event_id)`. If duplicates occur:
1. Verify provider is sending unique event_id
2. Check for concurrent processing issues
3. Review transaction isolation settings

## Best Practices

1. **Idempotency**: Always use unique identifiers for events
2. **Monitoring**: Set up alerts for failed events
3. **Cleanup**: Regularly clean up old processed events
4. **Testing**: Test webhook handling with provider's test mode
5. **Logging**: Log all webhook processing for audit trails
6. **Security**: Always verify webhook signatures in production
7. **Performance**: Consider partitioning tables for high volume
8. **Retry Strategy**: Use exponential backoff for retries

## Future Enhancements

1. **Dead Letter Queue**: Move events exceeding max retries to DLQ
2. **Webhook Replay**: Allow replaying processed webhooks
3. **Event Streaming**: Integrate with Kafka/RabbitMQ
4. **Metrics Dashboard**: Real-time monitoring UI
5. **Circuit Breaker**: Prevent cascading failures
6. **Rate Limiting**: Throttle webhook processing
