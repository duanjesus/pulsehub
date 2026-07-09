CREATE TABLE notifications (
    id                       BIGSERIAL PRIMARY KEY,
    user_id                  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    type                     VARCHAR(30) NOT NULL,
    title                    VARCHAR(150) NOT NULL,
    body                     VARCHAR(500) NOT NULL,
    related_conversation_id  BIGINT REFERENCES conversations (id) ON DELETE CASCADE,
    read_at                  TIMESTAMP,
    created_at               TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_unread ON notifications (user_id, read_at) WHERE read_at IS NULL;
