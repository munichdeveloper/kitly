-- Update invitations table to use token_hash instead of token
ALTER TABLE invitations RENAME COLUMN token TO token_hash;

-- Add seat limit and entitlement version to subscriptions table
ALTER TABLE subscriptions ADD COLUMN max_seats INTEGER;
ALTER TABLE subscriptions ADD COLUMN entitlement_version BIGINT DEFAULT 0;

-- Update index name for token_hash
DROP INDEX IF EXISTS idx_invitations_token;
CREATE INDEX idx_invitations_token_hash ON invitations(token_hash);

-- Set default max_seats based on plan for existing subscriptions
UPDATE subscriptions SET max_seats = 3 WHERE plan = 'FREE' AND max_seats IS NULL;
UPDATE subscriptions SET max_seats = 10 WHERE plan = 'STARTER' AND max_seats IS NULL;
UPDATE subscriptions SET max_seats = 50 WHERE plan = 'PROFESSIONAL' AND max_seats IS NULL;
-- ENTERPRISE has no limit (max_seats = NULL)
