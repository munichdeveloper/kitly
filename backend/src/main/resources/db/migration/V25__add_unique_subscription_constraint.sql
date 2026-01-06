-- Identify and delete duplicate subscriptions, keeping the most recently updated one
DELETE FROM subscriptions s1
USING subscriptions s2
WHERE s1.stripe_subscription_id = s2.stripe_subscription_id
  AND s1.stripe_subscription_id IS NOT NULL
  AND s1.updated_at < s2.updated_at;

-- If updated_at is same, tie-break with ID (keep higher ID which usually means inserted later)
DELETE FROM subscriptions s1
USING subscriptions s2
WHERE s1.stripe_subscription_id = s2.stripe_subscription_id
  AND s1.stripe_subscription_id IS NOT NULL
  AND s1.id < s2.id;

-- Add Unique Constraint
ALTER TABLE subscriptions
ADD CONSTRAINT uk_subscriptions_stripe_id UNIQUE (stripe_subscription_id);

