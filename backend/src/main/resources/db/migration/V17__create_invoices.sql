CREATE TABLE invoices (
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    stripe_invoice_id VARCHAR(255) NOT NULL UNIQUE,
    amount_paid BIGINT,
    currency VARCHAR(10),
    status VARCHAR(50),
    invoice_pdf TEXT,
    hosted_invoice_url TEXT,
    created_at TIMESTAMP WITHOUT TIME ZONE DEFAULT NOW()
);

CREATE INDEX idx_invoices_tenant_id ON invoices(tenant_id);

ALTER TABLE subscriptions ADD COLUMN stripe_subscription_id VARCHAR(255);
CREATE INDEX idx_subscriptions_stripe_id ON subscriptions(stripe_subscription_id);


