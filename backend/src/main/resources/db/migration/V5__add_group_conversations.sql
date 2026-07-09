-- Extend conversations to support groups alongside the existing 1:1 model.
ALTER TABLE conversations
    ADD COLUMN type       VARCHAR(20) NOT NULL DEFAULT 'DIRECT',
    ADD COLUMN name       VARCHAR(100),
    ADD COLUMN created_by BIGINT REFERENCES users (id),
    ADD COLUMN direct_key VARCHAR(50);

UPDATE conversations SET direct_key = user_one_id || '_' || user_two_id;

CREATE UNIQUE INDEX uq_conversations_direct_key ON conversations (direct_key) WHERE type = 'DIRECT';

-- Every conversation (direct or group) now has an explicit participant roster,
-- replacing the old fixed user_one_id/user_two_id pair.
CREATE TABLE conversation_participants (
    id              BIGSERIAL PRIMARY KEY,
    conversation_id BIGINT NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    user_id         BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role            VARCHAR(20) NOT NULL DEFAULT 'MEMBER',
    joined_at       TIMESTAMP NOT NULL DEFAULT now(),
    left_at         TIMESTAMP,
    CONSTRAINT uq_conversation_participant UNIQUE (conversation_id, user_id)
);

CREATE INDEX idx_conversation_participants_user ON conversation_participants (user_id);
CREATE INDEX idx_conversation_participants_conversation ON conversation_participants (conversation_id);

INSERT INTO conversation_participants (conversation_id, user_id, role, joined_at)
SELECT id, user_one_id, 'MEMBER', created_at FROM conversations
UNION ALL
SELECT id, user_two_id, 'MEMBER', created_at FROM conversations;

ALTER TABLE conversations DROP COLUMN user_one_id;
ALTER TABLE conversations DROP COLUMN user_two_id;

-- Per-participant read tracking replaces the single messages.read_at column,
-- since a group message can be read by anywhere from 0 to N-1 other members.
CREATE TABLE message_reads (
    id         BIGSERIAL PRIMARY KEY,
    message_id BIGINT NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
    user_id    BIGINT NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    read_at    TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_message_read UNIQUE (message_id, user_id)
);

CREATE INDEX idx_message_reads_message ON message_reads (message_id);

INSERT INTO message_reads (message_id, user_id, read_at)
SELECT m.id, cp.user_id, m.read_at
FROM messages m
JOIN conversation_participants cp ON cp.conversation_id = m.conversation_id AND cp.user_id <> m.sender_id
WHERE m.read_at IS NOT NULL;

ALTER TABLE messages DROP COLUMN read_at;
