ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS failed_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS sent_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS updated_at BIGINT;

ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS created_at BIGINT;

ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS status VARCHAR(64);

ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS title TEXT;

ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS body TEXT;

ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS channel VARCHAR(32);

ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS event_id VARCHAR(255);

ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS user_id VARCHAR(255);

ALTER TABLE IF EXISTS notification_history
    ADD COLUMN IF NOT EXISTS notification_id VARCHAR(255);

CREATE INDEX IF NOT EXISTS idx_notification_history_user_created_at
    ON notification_history (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_history_event_id
    ON notification_history (event_id);
