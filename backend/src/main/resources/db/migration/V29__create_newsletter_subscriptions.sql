-- V29: Create newsletter_subscriptions table with Double Opt-In support
-- This migration creates the newsletter_subscriptions table for managing newsletter subscriptions
-- with Double Opt-In mechanism (GDPR compliant)

CREATE TABLE newsletter_subscriptions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    channel VARCHAR(50) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT true,
    confirmed BOOLEAN NOT NULL DEFAULT false,
    subscribed_at TIMESTAMP NOT NULL,
    confirmed_at TIMESTAMP,
    unsubscribed_at TIMESTAMP,
    confirmation_token VARCHAR(100),
    unsubscribe_token VARCHAR(100),
    first_name VARCHAR(100),
    last_name VARCHAR(100),
    locale VARCHAR(10),
    CONSTRAINT uk_newsletter_email_channel UNIQUE (email, channel)
);

-- Create indexes for better performance
CREATE INDEX idx_newsletter_channel_active ON newsletter_subscriptions(channel, active);
CREATE INDEX idx_newsletter_email_active ON newsletter_subscriptions(email, active);
CREATE INDEX idx_newsletter_confirmation_token ON newsletter_subscriptions(confirmation_token);
CREATE INDEX idx_newsletter_unsubscribe_token ON newsletter_subscriptions(unsubscribe_token);

-- Add comments for documentation
COMMENT ON TABLE newsletter_subscriptions IS 'Stores newsletter subscriptions for various channels with Double Opt-In';
COMMENT ON COLUMN newsletter_subscriptions.email IS 'Email address of the subscriber';
COMMENT ON COLUMN newsletter_subscriptions.channel IS 'Newsletter channel (e.g. product_updates, marketing, technical)';
COMMENT ON COLUMN newsletter_subscriptions.active IS 'Indicates if the subscription is active';
COMMENT ON COLUMN newsletter_subscriptions.confirmed IS 'Indicates if the email address was confirmed via Double Opt-In';
COMMENT ON COLUMN newsletter_subscriptions.subscribed_at IS 'Timestamp when the subscription was created';
COMMENT ON COLUMN newsletter_subscriptions.confirmed_at IS 'Timestamp when the subscription was confirmed (if applicable)';
COMMENT ON COLUMN newsletter_subscriptions.unsubscribed_at IS 'Timestamp when the subscription was cancelled (if applicable)';
COMMENT ON COLUMN newsletter_subscriptions.confirmation_token IS 'Token for Double Opt-In email confirmation';
COMMENT ON COLUMN newsletter_subscriptions.unsubscribe_token IS 'Token for one-click unsubscribe via email link';
COMMENT ON COLUMN newsletter_subscriptions.locale IS 'Preferred language for newsletters (e.g. de_DE, en_US)';
