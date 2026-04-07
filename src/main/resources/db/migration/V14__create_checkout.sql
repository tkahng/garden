-- New permissions for order management
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000033', 'order:read',  'order', 'read',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000034', 'order:write', 'order', 'write', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- MANAGER and OWNER get order permissions
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name IN ('MANAGER', 'OWNER')
  AND p.name IN ('order:read', 'order:write')
ON CONFLICT DO NOTHING;

-- checkout schema
CREATE SCHEMA IF NOT EXISTS checkout;

-- checkout.carts
CREATE TABLE checkout.carts (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    status     TEXT        NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'CHECKED_OUT', 'ABANDONED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE UNIQUE INDEX idx_carts_user_active ON checkout.carts (user_id) WHERE status = 'ACTIVE';
CREATE INDEX idx_carts_user_id ON checkout.carts (user_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON checkout.carts
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- checkout.cart_items
CREATE TABLE checkout.cart_items (
    id          UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    cart_id     UUID           NOT NULL REFERENCES checkout.carts(id) ON DELETE CASCADE,
    variant_id  UUID           NOT NULL REFERENCES catalog.product_variants(id),
    quantity    INT            NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(19, 4) NOT NULL,
    created_at  TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_cart_items_cart_id ON checkout.cart_items (cart_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON checkout.cart_items
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- checkout.orders
CREATE TABLE checkout.orders (
    id                      UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id                 UUID           NOT NULL REFERENCES auth.users(id),
    status                  TEXT           NOT NULL DEFAULT 'PENDING_PAYMENT'
                                           CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'REFUNDED')),
    stripe_session_id       TEXT,
    stripe_payment_intent_id TEXT,
    total_amount            NUMERIC(19, 4) NOT NULL,
    currency                TEXT           NOT NULL DEFAULT 'usd',
    created_at              TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at              TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_orders_user_id           ON checkout.orders (user_id);
CREATE INDEX idx_orders_status            ON checkout.orders (status);
CREATE INDEX idx_orders_stripe_session_id ON checkout.orders (stripe_session_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON checkout.orders
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- checkout.order_items
CREATE TABLE checkout.order_items (
    id         UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id   UUID           NOT NULL REFERENCES checkout.orders(id) ON DELETE CASCADE,
    variant_id UUID           NOT NULL REFERENCES catalog.product_variants(id),
    quantity   INT            NOT NULL CHECK (quantity > 0),
    unit_price NUMERIC(19, 4) NOT NULL,
    created_at TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_order_items_order_id ON checkout.order_items (order_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON checkout.order_items
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
