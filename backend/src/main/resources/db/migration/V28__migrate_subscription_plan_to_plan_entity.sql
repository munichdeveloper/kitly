-- V28: Migrate subscriptions from enum plan to plan_id reference
-- This migration converts the static plan enum to a dynamic reference to the plans table

-- Step 1: Add new plan_id column (nullable initially for migration)
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS plan_id UUID;

-- Step 2: Create a temporary mapping from old plan names to plan codes
-- Assuming you have plans with codes: FREE, STARTER, BUSINESS, ENTERPRISE in the plans table

-- Step 3: Migrate existing data
-- Map old enum values to plan_id based on plan code
UPDATE subscriptions s
SET plan_id = (SELECT p.id FROM plans p WHERE p.code = s.plan)
WHERE s.plan IS NOT NULL;

-- Step 4: For any subscriptions without a matching plan, try to create or use a default plan
-- This handles edge cases where plans might not exist yet
DO $$
DECLARE
    default_plan_id UUID;
BEGIN
    -- Try to find or create a FREE plan as default
    SELECT id INTO default_plan_id FROM plans WHERE code = 'FREE' LIMIT 1;

    IF default_plan_id IS NULL THEN
        -- Create a default FREE plan if it doesn't exist
        INSERT INTO plans (id, code, name, description, is_active, display_order, created_at, updated_at)
        VALUES (gen_random_uuid(), 'FREE', 'Free Plan', 'Default free plan', true, 0, NOW(), NOW())
        RETURNING id INTO default_plan_id;
    END IF;

    -- Set default plan for subscriptions that still have no plan_id
    UPDATE subscriptions
    SET plan_id = default_plan_id
    WHERE plan_id IS NULL;
END $$;

-- Step 5: Make plan_id NOT NULL and add foreign key constraint
ALTER TABLE subscriptions ALTER COLUMN plan_id SET NOT NULL;
ALTER TABLE subscriptions ADD CONSTRAINT fk_subscriptions_plan
    FOREIGN KEY (plan_id) REFERENCES plans(id);

-- Step 6: Create index for better query performance
CREATE INDEX IF NOT EXISTS idx_subscriptions_plan_id ON subscriptions(plan_id);

-- Step 7: Drop the old plan column (VARCHAR/ENUM)
-- Note: Uncomment this after verifying the migration worked
-- ALTER TABLE subscriptions DROP COLUMN IF EXISTS plan;

-- Add comment for documentation
COMMENT ON COLUMN subscriptions.plan_id IS 'Reference to the dynamic plan in the plans table (replaces the static SubscriptionPlan enum)';

