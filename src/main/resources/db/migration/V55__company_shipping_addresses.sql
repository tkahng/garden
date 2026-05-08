-- Company-level shipping address book for B2B

CREATE TABLE b2b.company_shipping_addresses (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id  UUID        NOT NULL REFERENCES b2b.companies(id) ON DELETE CASCADE,
    label       TEXT,
    first_name  TEXT        NOT NULL,
    last_name   TEXT        NOT NULL,
    company     TEXT,
    address1    TEXT        NOT NULL,
    address2    TEXT,
    city        TEXT        NOT NULL,
    province    TEXT,
    zip         TEXT        NOT NULL,
    country     TEXT        NOT NULL,
    is_default  BOOLEAN     NOT NULL DEFAULT false,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_company_shipping_addresses_company_id
    ON b2b.company_shipping_addresses (company_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON b2b.company_shipping_addresses
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
