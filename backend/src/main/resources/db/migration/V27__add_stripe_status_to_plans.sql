-- V27: Add stripe_status column to plans table
-- This column tracks the actual Stripe API validation status for each plan

-- Add stripe_status column
ALTER TABLE plans ADD COLUMN IF NOT EXISTS stripe_status VARCHAR(50);

-- Create index for faster queries on stripe_status
CREATE INDEX IF NOT EXISTS idx_plans_stripe_status ON plans(stripe_status);

-- Set default status for existing plans (will be updated on first validation)
UPDATE plans SET stripe_status = 'not_configured' WHERE stripe_status IS NULL;

-- Add column comment for documentation
COMMENT ON COLUMN plans.stripe_status IS 'Stripe validation status: active (plan active + stripe active), inactive (plan inactive + stripe active), stripe_inactive (stripe inactive), unavailable (not found in stripe), not_configured (no price id configured)';

