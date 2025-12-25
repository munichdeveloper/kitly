-- Create platform_settings table for global application settings
CREATE TABLE platform_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    setting_key VARCHAR(100) NOT NULL UNIQUE,
    setting_value TEXT,
    setting_type VARCHAR(50) NOT NULL,
    description VARCHAR(500),
    is_encrypted BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by UUID REFERENCES users(id)
);

-- Create index on setting_key for faster lookups
CREATE INDEX idx_platform_settings_key ON platform_settings(setting_key);

-- Insert default platform settings
INSERT INTO platform_settings (setting_key, setting_value, setting_type, description, is_encrypted)
VALUES
    ('stripe.mode', 'test', 'STRING', 'Stripe API mode: test or live', FALSE),
    ('stripe.test.api_key', '', 'STRING', 'Stripe Test Mode API Key', TRUE),
    ('stripe.test.webhook_secret', '', 'STRING', 'Stripe Test Mode Webhook Secret', TRUE),
    ('stripe.live.api_key', '', 'STRING', 'Stripe Live Mode API Key', TRUE),
    ('stripe.live.webhook_secret', '', 'STRING', 'Stripe Live Mode Webhook Secret', TRUE),
    ('stripe.test.price.starter', '', 'STRING', 'Stripe Test Mode Starter Price ID', FALSE),
    ('stripe.test.price.business', '', 'STRING', 'Stripe Test Mode Business Price ID', FALSE),
    ('stripe.test.price.enterprise', '', 'STRING', 'Stripe Test Mode Enterprise Price ID', FALSE),
    ('stripe.live.price.starter', '', 'STRING', 'Stripe Live Mode Starter Price ID', FALSE),
    ('stripe.live.price.business', '', 'STRING', 'Stripe Live Mode Business Price ID', FALSE),
    ('stripe.live.price.enterprise', '', 'STRING', 'Stripe Live Mode Enterprise Price ID', FALSE);

-- Add PLATFORM_ADMIN role to roles table
INSERT INTO roles (id, name)
VALUES (gen_random_uuid(), 'ROLE_PLATFORM_ADMIN')
ON CONFLICT (name) DO NOTHING;

