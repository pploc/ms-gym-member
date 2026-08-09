-- Phase 8: remove Member-owned catalog; keep opaque plan/gym IDs + purchased snapshots.
ALTER TABLE subscriptions
    DROP CONSTRAINT IF EXISTS subscriptions_plan_id_fkey,
    DROP CONSTRAINT IF EXISTS subscriptions_gym_id_fkey;

ALTER TABLE subscriptions
    ADD COLUMN IF NOT EXISTS plan_type_snapshot VARCHAR(50),
    ADD COLUMN IF NOT EXISTS duration_days_snapshot INT,
    ADD COLUMN IF NOT EXISTS price_vnd_snapshot BIGINT;

-- Disposable pre-production data: backfill any leftover rows with neutral snapshot values.
UPDATE subscriptions
SET plan_type_snapshot = COALESCE(plan_type_snapshot, 'MONTHLY'),
    duration_days_snapshot = COALESCE(duration_days_snapshot, 30),
    price_vnd_snapshot = COALESCE(price_vnd_snapshot, 0)
WHERE plan_type_snapshot IS NULL
   OR price_vnd_snapshot IS NULL;

ALTER TABLE subscriptions
    ALTER COLUMN plan_type_snapshot SET NOT NULL,
    ALTER COLUMN price_vnd_snapshot SET NOT NULL;

CREATE TABLE IF NOT EXISTS pending_purchases (
    id VARCHAR(36) PRIMARY KEY,
    member_id VARCHAR(36) NOT NULL REFERENCES members(id),
    user_id VARCHAR(36) NOT NULL,
    gym_id VARCHAR(36) NOT NULL,
    plan_id VARCHAR(36) NOT NULL,
    plan_type_snapshot VARCHAR(50) NOT NULL,
    duration_days_snapshot INT,
    price_vnd_snapshot BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,
    payment_id VARCHAR(100),
    status VARCHAR(50) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_pending_purchases_member_status
    ON pending_purchases (member_id, status);
CREATE INDEX IF NOT EXISTS idx_pending_purchases_payment_id
    ON pending_purchases (payment_id);

DROP TABLE IF EXISTS membership_plans;
DROP TABLE IF EXISTS gym_locations;
