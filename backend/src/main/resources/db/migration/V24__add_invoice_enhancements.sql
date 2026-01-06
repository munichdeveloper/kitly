ALTER TABLE invoices
ADD COLUMN invoice_number VARCHAR(255),
ADD COLUMN amount_due BIGINT,
ADD COLUMN email_sent BOOLEAN DEFAULT FALSE NOT NULL,
ADD COLUMN email_scheduled_at TIMESTAMP;

CREATE INDEX idx_invoices_email_scheduled ON invoices(email_sent, email_scheduled_at);

