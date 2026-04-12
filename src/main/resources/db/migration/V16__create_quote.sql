-- Quote permissions for staff
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000035', 'quote:read',  'quote', 'read',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000036', 'quote:write', 'quote', 'write', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- STAFF, MANAGER, and OWNER get quote permissions
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name IN ('STAFF', 'MANAGER', 'OWNER')
  AND p.name IN ('quote:read', 'quote:write')
ON CONFLICT DO NOTHING;

-- Make product_variants.price nullable (null = quote-only)
ALTER TABLE catalog.product_variants
    ALTER COLUMN price DROP NOT NULL;

-- Quote schema
CREATE SCHEMA IF NOT EXISTS quote;

-- quote.quote_carts
CREATE TABLE quote.quote_carts (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status     TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUBMITTED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE UNIQUE INDEX idx_quote_carts_user_active
    ON quote.quote_carts (user_id) WHERE status = 'ACTIVE';

CREATE INDEX idx_quote_carts_user_id ON quote.quote_carts (user_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON quote.quote_carts
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- quote.quote_cart_items
CREATE TABLE quote.quote_cart_items (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    quote_cart_id UUID        NOT NULL REFERENCES quote.quote_carts(id) ON DELETE CASCADE,
    variant_id    UUID        NOT NULL REFERENCES catalog.product_variants(id),
    quantity      INT         NOT NULL CHECK (quantity > 0),
    note          TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_quote_cart_items_cart_id ON quote.quote_cart_items (quote_cart_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON quote.quote_cart_items
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- quote.quote_requests
CREATE TABLE quote.quote_requests (
    id                    UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id               UUID        NOT NULL REFERENCES auth.users(id),
    company_id            UUID        NOT NULL REFERENCES b2b.companies(id),
    assigned_staff_id     UUID        REFERENCES auth.users(id),
    status                TEXT        NOT NULL DEFAULT 'PENDING'
                                      CHECK (status IN ('PENDING','ASSIGNED','DRAFT','SENT','ACCEPTED','REJECTED','EXPIRED','CANCELLED')),
    delivery_address_line1 TEXT       NOT NULL,
    delivery_address_line2 TEXT,
    delivery_city          TEXT       NOT NULL,
    delivery_state         TEXT,
    delivery_postal_code   TEXT       NOT NULL,
    delivery_country       TEXT       NOT NULL,
    shipping_requirements  TEXT,
    customer_notes         TEXT,
    staff_notes            TEXT,
    expires_at             TIMESTAMPTZ,
    pdf_blob_id            UUID,
    order_id               UUID,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_quote_requests_user_id       ON quote.quote_requests (user_id);
CREATE INDEX idx_quote_requests_company_id    ON quote.quote_requests (company_id);
CREATE INDEX idx_quote_requests_status        ON quote.quote_requests (status);
CREATE INDEX idx_quote_requests_assigned_staff ON quote.quote_requests (assigned_staff_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON quote.quote_requests
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- quote.quote_items
CREATE TABLE quote.quote_items (
    id               UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    quote_request_id UUID           NOT NULL REFERENCES quote.quote_requests(id) ON DELETE CASCADE,
    variant_id       UUID,
    description      TEXT           NOT NULL,
    quantity         INT            NOT NULL CHECK (quantity > 0),
    unit_price       NUMERIC(19, 4),
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_quote_items_request_id ON quote.quote_items (quote_request_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON quote.quote_items
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
