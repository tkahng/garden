CREATE SCHEMA IF NOT EXISTS shipping;

CREATE TABLE shipping.shipping_zones (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    description   TEXT,
    country_codes TEXT[],
    provinces     TEXT[],
    is_active     BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER shipping_zones_updated_at BEFORE UPDATE ON shipping.shipping_zones
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE shipping.shipping_rates (
    id                 UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    zone_id            UUID           NOT NULL REFERENCES shipping.shipping_zones(id) ON DELETE CASCADE,
    name               VARCHAR(128)   NOT NULL,
    price              NUMERIC(19,4)  NOT NULL,
    min_weight_grams   INTEGER,
    max_weight_grams   INTEGER,
    min_order_amount   NUMERIC(19,4),
    estimated_days_min INTEGER,
    estimated_days_max INTEGER,
    carrier            VARCHAR(64),
    is_active          BOOLEAN        NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at         TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER shipping_rates_updated_at BEFORE UPDATE ON shipping.shipping_rates
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
