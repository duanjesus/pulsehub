CREATE TABLE messages (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    sender_id       BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    content         TEXT NOT NULL,
    sent_at         TIMESTAMP NOT NULL DEFAULT now(),
    read_at         TIMESTAMP
);

CREATE INDEX idx_messages_conversation ON messages (conversation_id, sent_at);
CREATE INDEX idx_messages_unread ON messages (conversation_id, read_at) WHERE read_at IS NULL;
