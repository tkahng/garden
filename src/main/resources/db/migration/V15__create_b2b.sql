-- B2B schema: companies and memberships

CREATE SCHEMA IF NOT EXISTS b2b;

-- b2b.companies
CREATE TABLE b2b.companies (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name                  TEXT        NOT NULL,
    tax_id                TEXT,
    phone                 TEXT,
    billing_address_line1 TEXT,
    billing_address_line2 TEXT,
    billing_city          TEXT,
    billing_state         TEXT,
    billing_postal_code   TEXT,
    billing_country       TEXT,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at            TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON b2b.companies
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- b2b.company_memberships
CREATE TABLE b2b.company_memberships (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id UUID        NOT NULL REFERENCES b2b.companies(id) ON DELETE CASCADE,
    user_id    UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    role       TEXT        NOT NULL DEFAULT 'MEMBER' CHECK (role IN ('OWNER', 'MEMBER')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON b2b.company_memberships
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE UNIQUE INDEX idx_company_memberships_company_user
    ON b2b.company_memberships (company_id, user_id);

CREATE INDEX idx_company_memberships_user_id ON b2b.company_memberships (user_id);
CREATE INDEX idx_company_memberships_company_id ON b2b.company_memberships (company_id);
