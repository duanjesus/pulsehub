CREATE TABLE conversations (
    id           BIGSERIAL PRIMARY KEY,
    user_one_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    user_two_id  BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_conversation_pair UNIQUE (user_one_id, user_two_id),
    CONSTRAINT chk_conversation_distinct_users CHECK (user_one_id <> user_two_id),
    CONSTRAINT chk_conversation_ordered_pair CHECK (user_one_id < user_two_id)
);

CREATE INDEX idx_conversations_user_one ON conversations (user_one_id);
CREATE INDEX idx_conversations_user_two ON conversations (user_two_id);
