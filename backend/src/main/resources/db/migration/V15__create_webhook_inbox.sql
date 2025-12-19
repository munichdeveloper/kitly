-- Create webhook_inbox table for Stripe webhook events (provider-agnostic)
CREATE TABLE webhook_inbox (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    provider VARCHAR(50) NOT NULL,
    event_id VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    payload JSONB NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    processed_at TIMESTAMP,
    error_message TEXT,
    retry_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT unique_provider_event UNIQUE (provider, event_id),
    CONSTRAINT check_status CHECK (status IN ('PENDING', 'PROCESSING', 'PROCESSED', 'FAILED'))
);

-- Create indexes for performance
CREATE INDEX idx_webhook_inbox_provider ON webhook_inbox(provider);
CREATE INDEX idx_webhook_inbox_event_type ON webhook_inbox(event_type);
CREATE INDEX idx_webhook_inbox_status ON webhook_inbox(status);
CREATE INDEX idx_webhook_inbox_created_at ON webhook_inbox(created_at);
CREATE INDEX idx_webhook_inbox_event_id ON webhook_inbox(event_id);
