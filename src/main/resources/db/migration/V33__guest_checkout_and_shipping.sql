-- Guest cart support: allow carts without a user (session-based)
ALTER TABLE checkout.carts
    ALTER COLUMN user_id DROP NOT NULL,
    ADD COLUMN session_id UUID;

CREATE UNIQUE INDEX idx_carts_session_id ON checkout.carts (session_id) WHERE session_id IS NOT NULL;

ALTER TABLE checkout.carts
    ADD CONSTRAINT cart_owner_check
        CHECK (user_id IS NOT NULL OR session_id IS NOT NULL);

-- Guest order support: allow orders without a user account
ALTER TABLE checkout.orders
    ALTER COLUMN user_id DROP NOT NULL,
    ADD COLUMN guest_email    TEXT,
    ADD COLUMN shipping_cost  NUMERIC(19, 4),
    ADD COLUMN shipping_rate_id UUID;

CREATE INDEX idx_orders_guest_email ON checkout.orders (guest_email)
    WHERE guest_email IS NOT NULL;

ALTER TABLE checkout.orders
    ADD CONSTRAINT order_owner_check
        CHECK (user_id IS NOT NULL OR guest_email IS NOT NULL);
