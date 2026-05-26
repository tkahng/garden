-- Rotating refresh tokens with 7-day expiry and reuse-detection chain.
-- Replaces the single-use REFRESH_TOKEN rows in auth.tokens for login/OAuth flows.
-- The legacy auth.tokens table is kept for EMAIL_VERIFICATION and PASSWORD_RESET.
CREATE TABLE IF NOT EXISTS auth.refresh_tokens (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    token_hash        TEXT        NOT NULL UNIQUE,
    user_id           UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    expires_at        TIMESTAMPTZ NOT NULL,
    revoked_at        TIMESTAMPTZ,
    -- Raw value of the replacement token issued when this token was rotated.
    -- Stored for audit / chain-revocation tracing; NULL until consumed.
    replaced_by_token TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_id
    ON auth.refresh_tokens (user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_token_hash
    ON auth.refresh_tokens (token_hash);
