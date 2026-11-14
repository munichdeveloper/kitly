-- #49: Stripe price IDs are stored per mode as stripe.<mode>.plan.<PLAN>
-- (like stripe.<mode>.api_key / stripe.<mode>.webhook_secret), so a switch of
-- stripe.mode also switches the price IDs.
--
-- Until now they were stored mode-independently as stripe.plan.<PLAN>. All
-- existing values are test-mode price IDs (production still runs in test mode),
-- so they are copied to stripe.test.plan.<PLAN>. Live price IDs have to be
-- entered in the platform admin (stripe.live.plan.<PLAN>) before switching to live.
--
-- The legacy stripe.plan.* rows are kept (not deleted) for reference/rollback;
-- the application no longer reads them. Existing stripe.test.plan.* rows win.
-- stripe.plan.<PLAN>.active rows are activation flags, not price IDs, and are
-- not copied. The legacy stripe.{test,live}.price.* rows from V19 stay untouched.
INSERT INTO platform_settings (setting_key, setting_value, setting_type, description, is_encrypted)
SELECT 'stripe.test.plan.' || substring(setting_key FROM char_length('stripe.plan.') + 1),
       setting_value,
       setting_type,
       'Stripe Test Mode Price ID for plan ' || substring(setting_key FROM char_length('stripe.plan.') + 1)
           || ' (copied from ' || setting_key || ')',
       is_encrypted
FROM platform_settings
WHERE setting_key LIKE 'stripe.plan.%'
  AND setting_key NOT LIKE '%.active'
  AND char_length(setting_key) > char_length('stripe.plan.')
  AND char_length(setting_key) <= 95
ON CONFLICT (setting_key) DO NOTHING;
