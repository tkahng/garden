-- Company invitation tokens for onboarding new members
CREATE TABLE b2b.company_invitations (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id   UUID        NOT NULL REFERENCES b2b.companies(id) ON DELETE CASCADE,
    email        TEXT        NOT NULL,
    role         TEXT        NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('MANAGER', 'MEMBER')),
    spending_limit NUMERIC(19,4),
    token        UUID        NOT NULL UNIQUE DEFAULT gen_random_uuid(),
    invited_by   UUID        NOT NULL REFERENCES auth.users(id),
    status       TEXT        NOT NULL DEFAULT 'PENDING'
                             CHECK (status IN ('PENDING', 'ACCEPTED', 'CANCELLED', 'EXPIRED')),
    expires_at   TIMESTAMPTZ NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON b2b.company_invitations
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE INDEX idx_company_invitations_company_id ON b2b.company_invitations (company_id);
CREATE INDEX idx_company_invitations_token      ON b2b.company_invitations (token);
CREATE INDEX idx_company_invitations_email      ON b2b.company_invitations (email);
