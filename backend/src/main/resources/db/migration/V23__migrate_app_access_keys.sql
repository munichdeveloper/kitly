-- Migration script to update entitlement keys from old format to new format
-- Old format: app.nim.access
-- New format: app_access.nim

-- Update entitlements table (tenant-specific overrides)
UPDATE entitlements
SET feature_key = 'app_access.nim'
WHERE feature_key = 'app.nim.access';

-- Note: The plan entitlements in plan_entitlements table will be created by V22 migration
-- with the correct format, so no update needed there.

-- If you have any custom scripts or configurations that reference the old key,
-- you'll need to update them manually to use 'app_access.nim' instead of 'app.nim.access'.

