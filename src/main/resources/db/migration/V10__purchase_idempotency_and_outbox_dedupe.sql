ALTER TABLE pending_purchases
    ADD COLUMN idempotency_key VARCHAR(128);

UPDATE pending_purchases
SET idempotency_key = id
WHERE idempotency_key IS NULL;

ALTER TABLE pending_purchases
    ALTER COLUMN idempotency_key SET NOT NULL;

ALTER TABLE pending_purchases
    ADD CONSTRAINT uk_pending_purchase_user_idempotency UNIQUE (user_id, idempotency_key);

ALTER TABLE outbox_events
    ADD COLUMN dedupe_key VARCHAR(255);

CREATE UNIQUE INDEX uk_outbox_dedupe_key
    ON outbox_events (dedupe_key)
    WHERE dedupe_key IS NOT NULL;
