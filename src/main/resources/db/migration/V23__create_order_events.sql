-- Expand status CHECK constraint to include fulfillment states
ALTER TABLE checkout.orders DROP CONSTRAINT IF EXISTS orders_status_check;
ALTER TABLE checkout.orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'REFUNDED', 'PARTIALLY_FULFILLED', 'FULFILLED'));

CREATE TABLE checkout.order_events (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id    UUID        NOT NULL REFERENCES checkout.orders(id),
    type        VARCHAR(64) NOT NULL,
    message     TEXT,
    author_id   UUID,
    author_name VARCHAR(128),
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX order_events_order_id_idx ON checkout.order_events (order_id, created_at);
