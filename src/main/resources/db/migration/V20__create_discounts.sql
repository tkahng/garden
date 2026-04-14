CREATE TABLE checkout.discounts (
    id              UUID PRIMARY KEY,
    code            VARCHAR(64) NOT NULL,
    type            VARCHAR(32) NOT NULL,
    value           NUMERIC(19,4) NOT NULL DEFAULT 0,
    min_order_amount NUMERIC(19,4),
    max_uses        INTEGER,
    used_count      INTEGER NOT NULL DEFAULT 0,
    starts_at       TIMESTAMPTZ,
    ends_at         TIMESTAMPTZ,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX discounts_code_uq ON checkout.discounts (UPPER(code));
CREATE TRIGGER discounts_updated_at BEFORE UPDATE ON checkout.discounts
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

ALTER TABLE checkout.orders
    ADD COLUMN discount_id     UUID REFERENCES checkout.discounts(id),
    ADD COLUMN discount_amount NUMERIC(19,4);
