-- Persistence entities store externally supplied identifiers as strings.
ALTER TABLE subscriptions
    DROP CONSTRAINT IF EXISTS subscriptions_member_id_fkey,
    DROP CONSTRAINT IF EXISTS subscriptions_plan_id_fkey,
    DROP CONSTRAINT IF EXISTS subscriptions_gym_id_fkey;
ALTER TABLE membership_plans
    DROP CONSTRAINT IF EXISTS membership_plans_gym_id_fkey;

ALTER TABLE gym_locations
    ALTER COLUMN id TYPE VARCHAR(36) USING id::text,
    ALTER COLUMN chain_id TYPE VARCHAR(36) USING chain_id::text;
ALTER TABLE members
    ALTER COLUMN id TYPE VARCHAR(36) USING id::text,
    ALTER COLUMN user_id TYPE VARCHAR(36) USING user_id::text;
ALTER TABLE membership_plans
    ALTER COLUMN id TYPE VARCHAR(36) USING id::text,
    ALTER COLUMN gym_id TYPE VARCHAR(36) USING gym_id::text;
ALTER TABLE subscriptions
    ALTER COLUMN id TYPE VARCHAR(36) USING id::text,
    ALTER COLUMN member_id TYPE VARCHAR(36) USING member_id::text,
    ALTER COLUMN plan_id TYPE VARCHAR(36) USING plan_id::text,
    ALTER COLUMN gym_id TYPE VARCHAR(36) USING gym_id::text;

ALTER TABLE membership_plans
    ADD CONSTRAINT membership_plans_gym_id_fkey
        FOREIGN KEY (gym_id) REFERENCES gym_locations(id);
ALTER TABLE subscriptions
    ADD CONSTRAINT subscriptions_member_id_fkey
        FOREIGN KEY (member_id) REFERENCES members(id),
    ADD CONSTRAINT subscriptions_plan_id_fkey
        FOREIGN KEY (plan_id) REFERENCES membership_plans(id),
    ADD CONSTRAINT subscriptions_gym_id_fkey
        FOREIGN KEY (gym_id) REFERENCES gym_locations(id);
