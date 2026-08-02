-- Validate existing data before enforcing one current subscription per member.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM subscriptions
        WHERE status IN ('ACTIVE', 'PAUSED')
        GROUP BY member_id
        HAVING COUNT(*) > 1
    ) THEN
        RAISE EXCEPTION 'Cannot enforce one current subscription per member: duplicate current rows exist';
    END IF;
END $$;

CREATE UNIQUE INDEX uq_subscriptions_one_current_per_member
    ON subscriptions (member_id)
    WHERE status IN ('ACTIVE', 'PAUSED');

CREATE INDEX idx_subscriptions_member_status
    ON subscriptions (member_id, status);

CREATE INDEX idx_subscriptions_status_end_date
    ON subscriptions (status, end_date);

CREATE INDEX idx_members_gym_id
    ON members (gym_id);

CREATE INDEX idx_members_status_gym_id
    ON members (status, gym_id);

CREATE INDEX idx_membership_plans_gym_active
    ON membership_plans (gym_id, active);
