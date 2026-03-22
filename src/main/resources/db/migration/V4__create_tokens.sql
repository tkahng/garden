-- Stateful tokens: refresh, email verification, password reset.
-- token_hash is SHA-256 of the opaque token string.
-- Hard-deleted on use (rotation = delete old, insert new).
-- Note: expired tokens that are never used accumulate; add a cleanup job in Phase 2.
CREATE TABLE tokens (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id     UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type        TEXT        NOT NULL,
    token_hash  TEXT        NOT NULL UNIQUE,
    expires_at  TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_tokens_user_type ON tokens(user_id, type);
