CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(150) NOT NULL UNIQUE,
    password        VARCHAR(255) NOT NULL,
    avatar_url      VARCHAR(255),
    status          VARCHAR(20) NOT NULL DEFAULT 'OFFLINE',
    last_activity_at TIMESTAMP,
    last_seen_at    TIMESTAMP,
    active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_users_status ON users (status);
