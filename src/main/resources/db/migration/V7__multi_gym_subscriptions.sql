-- Drop gym_id from members (profile is chain-wide)
ALTER TABLE members DROP COLUMN IF EXISTS gym_id;

-- Add gym_id to subscriptions (subscription is gym-specific)
ALTER TABLE subscriptions ADD COLUMN IF NOT EXISTS gym_id UUID REFERENCES gym_locations(id);

-- Backfill subscriptions.gym_id from membership_plans
UPDATE subscriptions s
SET gym_id = p.gym_id
FROM membership_plans p
WHERE s.plan_id = p.id AND s.gym_id IS NULL;

-- Enforce gym_id NOT NULL on subscriptions
ALTER TABLE subscriptions ALTER COLUMN gym_id SET NOT NULL;

-- Replace single-gym current subscription index with (member_id, gym_id) current subscription index
DROP INDEX IF EXISTS uq_subscriptions_one_current_per_member;

CREATE UNIQUE INDEX uq_subscriptions_one_current_per_member_gym
    ON subscriptions (member_id, gym_id)
    WHERE status IN ('ACTIVE', 'PAUSED');

CREATE INDEX idx_subscriptions_member_gym_status
    ON subscriptions (member_id, gym_id, status);
