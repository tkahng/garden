-- Short-lived tokens for admin impersonation of customer accounts
CREATE TABLE IF NOT EXISTS auth.impersonation_tokens (
    id              UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    target_user_id  UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    admin_user_id   UUID        NOT NULL,
    token_hash      TEXT        NOT NULL UNIQUE,
    expires_at      TIMESTAMPTZ NOT NULL,
    used_at         TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_impersonation_tokens_token_hash
    ON auth.impersonation_tokens (token_hash);

CREATE INDEX IF NOT EXISTS idx_impersonation_tokens_target_user_id
    ON auth.impersonation_tokens (target_user_id);
