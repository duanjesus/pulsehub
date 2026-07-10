-- Voice messages: a message is either a text body or a voice attachment.
ALTER TABLE messages
    ADD COLUMN type                        VARCHAR(20) NOT NULL DEFAULT 'TEXT',
    ADD COLUMN attachment_url              VARCHAR(255),
    ADD COLUMN attachment_duration_seconds INTEGER;

ALTER TABLE messages ALTER COLUMN content DROP NOT NULL;

ALTER TABLE messages
    ADD CONSTRAINT chk_message_body CHECK (
        (type = 'TEXT' AND content IS NOT NULL AND attachment_url IS NULL) OR
        (type = 'VOICE' AND attachment_url IS NOT NULL AND content IS NULL)
    );

-- Web Push subscriptions, one row per browser/device the user has opted in from.
CREATE TABLE push_subscriptions (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    endpoint   VARCHAR(500) NOT NULL,
    p256dh_key VARCHAR(255) NOT NULL,
    auth_key   VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_push_subscription_endpoint UNIQUE (endpoint)
);

CREATE INDEX idx_push_subscriptions_user ON push_subscriptions (user_id);
