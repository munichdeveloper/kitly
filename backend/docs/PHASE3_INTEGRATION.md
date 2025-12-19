# Phase 3: Integration & Frontend Implementation Guide

## Overview

Phase 3 adds comprehensive billing integration, webhook handling, event sourcing patterns, and frontend UI for subscription management. This phase builds on the multi-tenant architecture from Phase 2.

## What's New in Phase 3

### Backend Features

1. **Webhook Inbox Pattern**
   - Receive webhooks from external providers (Stripe, etc.)
   - Idempotent processing with duplicate detection
   - Automatic retry logic for failed webhooks
   - Admin endpoints for monitoring and management

2. **Outbox Event Pattern**
   - Reliable event publishing to external systems
   - Transactional event storage
   - Automatic retry with configurable limits
   - Event cleanup for old processed events

3. **Billing Service**
   - Subscription lifecycle management
   - Plan changes and upgrades
   - Payment event handling
   - Integration with entitlement system

4. **Scheduled Background Tasks**
   - Automated webhook processing (every 30s)
   - Automated event publishing (every 30s)
   - Failed event retry (every 5 min)
   - Old event cleanup (daily)

### Frontend Features

1. **Subscription Management Page**
   - View current plan and status
   - Display entitlements and limits
   - Browse available plans
   - Seat usage tracking

2. **Enhanced API Client**
   - Tenant management endpoints
   - Subscription endpoints
   - Entitlement endpoints
   - Membership endpoints

## Architecture

### Event Flow

```
┌─────────────────────┐
│  External Provider  │
│   (e.g., Stripe)   │
└──────────┬──────────┘
           │ webhook
           ▼
┌─────────────────────┐
│ WebhookController   │
│  /api/webhooks/*   │
└──────────┬──────────┘
           │ store
           ▼
┌─────────────────────┐
│  webhook_inbox      │
│  (Database)         │
└──────────┬──────────┘
           │ scheduled
           ▼
┌─────────────────────┐
│  WebhookService     │
│  (Process)          │
└──────────┬──────────┘
           │ business logic
           ▼
┌─────────────────────┐
│  BillingService     │
│  (Update DB)        │
└──────────┬──────────┘
           │ publish
           ▼
┌─────────────────────┐
│  outbox_events      │
│  (Database)         │
└──────────┬──────────┘
           │ scheduled
           ▼
┌─────────────────────┐
│  OutboxService      │
│  (Publish)          │
└──────────┬──────────┘
           │
           ▼
┌─────────────────────┐
│  External Systems   │
│  (Analytics, etc.)  │
└─────────────────────┘
```

## Getting Started

### Prerequisites

- Completed Phase 1 (Basic auth) and Phase 2 (Multi-tenancy)
- PostgreSQL database running
- Node.js 18+ for frontend
- Java 17+ for backend

### Backend Setup

1. **Verify Database Migrations**

The webhook and outbox tables are automatically created via Flyway migrations:
- `V15__create_webhook_inbox.sql`
- `V16__create_outbox_events.sql`

2. **Start the Backend**

```bash
cd backend
mvn spring-boot:run
```

The application will automatically:
- Create database tables
- Start scheduled tasks
- Enable webhook endpoints

3. **Verify Services**

Check logs for:
```
Running scheduled task: processPendingWebhooks
Running scheduled task: processPendingOutboxEvents
```

### Frontend Setup

1. **Install Dependencies**

```bash
cd frontend
npm install
```

2. **Start Development Server**

```bash
npm run dev
```

3. **Access the Application**

- Dashboard: http://localhost:3000/dashboard
- Subscriptions: http://localhost:3000/subscriptions

## API Endpoints

### Webhook Endpoints

#### Public Endpoints (No Authentication)

```
POST /api/webhooks/stripe
POST /api/webhooks/{provider}
```

#### Admin Endpoints (Requires ADMIN role)

```
GET  /api/webhooks/{id}
GET  /api/webhooks/provider/{provider}
GET  /api/webhooks/status/{status}
POST /api/webhooks/{id}/process
POST /api/webhooks/retry-failed
```

### Subscription Endpoints

```
GET  /api/plans
GET  /api/entitlements/me
GET  /api/tenants/{tenantId}/entitlements
```

## Testing

### Unit Tests

Run all tests:
```bash
cd backend
mvn test
```

New test classes:
- `WebhookServiceTest` - 8 tests for webhook processing
- `OutboxServiceTest` - 9 tests for event publishing

### Manual Testing

#### Test Webhook Reception

```bash
# Send a test webhook
curl -X POST http://localhost:8080/api/webhooks/stripe \
  -H "Content-Type: application/json" \
  -d '{
    "id": "evt_test_123",
    "type": "customer.subscription.created",
    "data": {
      "object": {
        "id": "sub_123",
        "status": "active"
      }
    }
  }'
```

#### Verify Webhook Storage

```bash
# Login as admin and check webhook
curl -X GET http://localhost:8080/api/webhooks/status/PENDING \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN"
```

#### Test Subscription Page

1. Login to the frontend
2. Navigate to http://localhost:3000/subscriptions
3. View current plan and entitlements
4. Browse available plans

## Configuration

### Scheduled Tasks

Control background processing via `application.yml`:

```yaml
scheduling:
  enabled: true  # Set to false to disable all scheduled tasks
```

### Webhook Settings

Configure retry behavior:

```java
@Scheduled(fixedDelay = 300000)  // 5 minutes
public void retryFailedWebhooks() {
    webhookService.retryFailedWebhooks(3);  // Max 3 retries
}
```

### Cleanup Settings

Configure retention period:

```java
@Scheduled(cron = "0 0 2 * * ?")  // Daily at 2 AM
public void cleanupOldOutboxEvents() {
    outboxService.cleanupOldEvents(30);  // Keep 30 days
}
```

## Integration with Stripe

### Setting Up Webhooks

1. **Create Stripe Webhook**

In Stripe Dashboard:
- Go to Developers → Webhooks
- Add endpoint: `https://yourdomain.com/api/webhooks/stripe`
- Select events: `customer.subscription.*`, `invoice.*`

2. **Verify Signature (Production)**

Implement signature verification:

```java
private boolean verifyStripeSignature(String signature, String payload) {
    // Use Stripe SDK to verify signature
    return Stripe.Webhook.constructEvent(
        payload, 
        signature, 
        webhookSecret
    );
}
```

3. **Handle Events**

The system automatically processes these Stripe events:
- `customer.subscription.created`
- `customer.subscription.updated`
- `customer.subscription.deleted`
- `invoice.payment_succeeded`
- `invoice.payment_failed`

## Monitoring

### Database Queries

Check webhook status:
```sql
SELECT status, COUNT(*) 
FROM webhook_inbox 
GROUP BY status;
```

Check failed events:
```sql
SELECT event_type, error_message, retry_count
FROM webhook_inbox
WHERE status = 'FAILED'
ORDER BY created_at DESC;
```

Check outbox backlog:
```sql
SELECT status, COUNT(*) 
FROM outbox_events 
GROUP BY status;
```

### Application Logs

Monitor these log patterns:
- `Processing X pending webhooks`
- `Successfully processed webhook: {id}`
- `Error processing webhook: {id}`
- `Retrying X failed webhooks`

## Security

### Webhook Security

1. **Verify Signatures**
   - Always verify webhook signatures in production
   - Use provider-specific verification methods

2. **Rate Limiting**
   - Consider adding rate limiting to webhook endpoints
   - Prevent abuse from malicious actors

3. **Access Control**
   - Webhook endpoints are public (required)
   - Admin endpoints require authentication
   - Sensitive data in payloads should be encrypted

### Data Privacy

1. **Webhook Payloads**
   - May contain customer payment information
   - Ensure database encryption at rest
   - Implement data retention policies

2. **Event Data**
   - Clean up old events regularly
   - Anonymize or encrypt sensitive data
   - Follow GDPR/CCPA requirements

## Troubleshooting

### Webhooks Not Processing

**Symptoms**: Webhooks stuck in PENDING status

**Solutions**:
1. Check scheduled tasks are enabled
2. Verify database connectivity
3. Check application logs for errors
4. Manually trigger processing via admin endpoint

### Events Not Publishing

**Symptoms**: Outbox events stuck in PENDING status

**Solutions**:
1. Verify external system is accessible
2. Check network connectivity
3. Review error messages in database
4. Manually retry via OutboxService

### Frontend Not Loading Data

**Symptoms**: Subscription page shows errors

**Solutions**:
1. Verify backend is running
2. Check browser console for errors
3. Verify API endpoints are accessible
4. Check user has required permissions

## Best Practices

### Development

1. **Testing**: Always test webhook handling in sandbox mode
2. **Logging**: Use structured logging for better debugging
3. **Monitoring**: Set up alerts for failed events
4. **Documentation**: Document custom webhook handlers

### Production

1. **Signature Verification**: Always verify webhook signatures
2. **Scaling**: Consider partitioning tables for high volume
3. **Monitoring**: Use APM tools for performance monitoring
4. **Backup**: Regular database backups for event data
5. **Cleanup**: Automated cleanup of old events

## Migration from Phase 2

No breaking changes from Phase 2. New features are additive:

1. Database migrations run automatically
2. Existing endpoints remain unchanged
3. New webhook endpoints are added
4. Frontend adds new pages without affecting existing ones

## Next Steps

After Phase 3 completion:

1. **Stripe Integration**: Implement full Stripe payment flow
2. **Email Notifications**: Add email alerts for subscription events
3. **Admin Dashboard**: Build admin UI for webhook monitoring
4. **Analytics**: Add usage analytics and reporting
5. **Mobile App**: Extend API for mobile clients

## Support

For issues or questions:

1. Check documentation: `/backend/docs/`
2. Review test cases for examples
3. Check application logs
4. Open GitHub issue with details

## References

- [WEBHOOKS_AND_OUTBOX.md](WEBHOOKS_AND_OUTBOX.md) - Detailed webhook/outbox documentation
- [ENTITLEMENTS.md](ENTITLEMENTS.md) - Entitlement system documentation
- Stripe Webhooks: https://stripe.com/docs/webhooks
- Outbox Pattern: https://microservices.io/patterns/data/transactional-outbox.html
