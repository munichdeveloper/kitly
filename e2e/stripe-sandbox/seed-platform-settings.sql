-- Seeds the `platform_settings` rows that point a running Kitly backend at a
-- REAL Stripe test-mode account for the nightly sandbox E2E test (issue #40).
--
-- Unlike backend/src/test/resources/db/testdata/R__seed_stripe_test_mode.sql
-- (which seeds deterministic fake placeholders for the mocked unit/
-- integration tests), the values inserted here come from actual Stripe
-- test-mode credentials passed in as psql variables by the CI workflow -
-- never hardcode real keys/secrets into this file.
--
-- Expected psql variables (see .github/workflows/stripe-sandbox-e2e.yml):
--   api_key          Stripe test-mode secret key (sk_test_...)
--   webhook_secret   Signing secret printed by `stripe listen` for this run
--   price_starter    Stripe test-mode Price ID for the STARTER plan
--   price_business   Optional: Price ID for the BUSINESS plan ('' to skip)
--   price_enterprise Optional: Price ID for the ENTERPRISE plan ('' to skip)
INSERT INTO platform_settings (setting_key, setting_value, setting_type, description, is_encrypted)
VALUES
    ('stripe.mode', 'test', 'STRING', 'Stripe API mode: test or live', FALSE),
    ('stripe.test.api_key', :'api_key', 'STRING', 'Stripe Test Mode API Key (sandbox E2E run)', TRUE),
    ('stripe.test.webhook_secret', :'webhook_secret', 'STRING', 'Stripe Test Mode Webhook Secret (from stripe listen, sandbox E2E run)', TRUE),
    ('stripe.plan.STARTER', :'price_starter', 'STRING', 'Stripe Test Mode Price ID for plan STARTER (sandbox E2E run)', FALSE)
ON CONFLICT (setting_key) DO UPDATE
    SET setting_value = EXCLUDED.setting_value,
        setting_type = EXCLUDED.setting_type,
        description = EXCLUDED.description,
        is_encrypted = EXCLUDED.is_encrypted;

INSERT INTO platform_settings (setting_key, setting_value, setting_type, description, is_encrypted)
SELECT 'stripe.plan.BUSINESS', :'price_business', 'STRING', 'Stripe Test Mode Price ID for plan BUSINESS (sandbox E2E run)', FALSE
WHERE :'price_business' <> ''
ON CONFLICT (setting_key) DO UPDATE
    SET setting_value = EXCLUDED.setting_value;

INSERT INTO platform_settings (setting_key, setting_value, setting_type, description, is_encrypted)
SELECT 'stripe.plan.ENTERPRISE', :'price_enterprise', 'STRING', 'Stripe Test Mode Price ID for plan ENTERPRISE (sandbox E2E run)', FALSE
WHERE :'price_enterprise' <> ''
ON CONFLICT (setting_key) DO UPDATE
    SET setting_value = EXCLUDED.setting_value;
