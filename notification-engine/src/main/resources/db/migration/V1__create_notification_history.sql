CREATE TABLE IF NOT EXISTS notification_history (
    id BIGSERIAL PRIMARY KEY,
    notification_id VARCHAR(255) NOT NULL UNIQUE,
    event_id VARCHAR(255) NOT NULL,
    user_id VARCHAR(255) NOT NULL,
    channel VARCHAR(32) NOT NULL,
    title TEXT,
    body TEXT,
    status VARCHAR(64) NOT NULL,
    created_at BIGINT NOT NULL,
    updated_at BIGINT,
    sent_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_notification_history_user_created_at
    ON notification_history (user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notification_history_event_id
    ON notification_history (event_id);
