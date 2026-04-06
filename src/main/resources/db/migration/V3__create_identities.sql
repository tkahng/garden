CREATE TABLE auth.identities (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id       UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    provider      TEXT        NOT NULL,
    account_id    TEXT        NOT NULL,
    password_hash TEXT,
    access_token  TEXT,
    refresh_token TEXT,
    id_token      TEXT,
    expires_at    TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (provider, account_id)
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON auth.identities
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
