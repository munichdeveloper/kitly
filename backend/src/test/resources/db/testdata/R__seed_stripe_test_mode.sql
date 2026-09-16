-- Versioned reference configuration for Stripe in test/CI environments.
--
-- This repeatable Flyway migration only runs for the "test" Spring profile
-- (see src/test/resources/application-test.yml, which adds
-- classpath:db/testdata as an extra Flyway location). It guarantees that
-- every local test run and every CI run starts from the same, known Stripe
-- test-mode configuration instead of depending on manually maintained
-- runtime settings in `platform_settings`.
--
-- None of these values are real Stripe credentials - "sk_test_..." /
-- "whsec_..." / "price_..." here are deterministic placeholders used only to
-- exercise StripeConfig, StripeWebhookController and WebhookProcessor
-- without ever calling the real Stripe API.
INSERT INTO platform_settings (setting_key, setting_value, setting_type, description, is_encrypted)
VALUES
    ('stripe.mode', 'test', 'STRING', 'Stripe API mode: test or live', FALSE),
    ('stripe.test.api_key', 'sk_test_kitly_ci_0000000000000000000000000', 'STRING', 'Stripe Test Mode API Key (CI fixture, not a real key)', TRUE),
    ('stripe.test.webhook_secret', 'whsec_kitly_ci_test_secret_00000000000000', 'STRING', 'Stripe Test Mode Webhook Secret (CI fixture, not a real secret)', TRUE),
    ('stripe.plan.STARTER', 'price_test_starter_ci', 'STRING', 'Stripe Test Mode Price ID for plan STARTER (CI fixture)', FALSE),
    ('stripe.plan.BUSINESS', 'price_test_business_ci', 'STRING', 'Stripe Test Mode Price ID for plan BUSINESS (CI fixture)', FALSE),
    ('stripe.plan.ENTERPRISE', 'price_test_enterprise_ci', 'STRING', 'Stripe Test Mode Price ID for plan ENTERPRISE (CI fixture)', FALSE)
ON CONFLICT (setting_key) DO UPDATE
    SET setting_value = EXCLUDED.setting_value,
        setting_type = EXCLUDED.setting_type,
        description = EXCLUDED.description,
        is_encrypted = EXCLUDED.is_encrypted;
