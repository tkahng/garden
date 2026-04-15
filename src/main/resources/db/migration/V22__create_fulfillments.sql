CREATE TABLE checkout.fulfillments (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id         UUID        NOT NULL REFERENCES checkout.orders(id),
    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    tracking_number  VARCHAR(128),
    tracking_company VARCHAR(64),
    tracking_url     TEXT,
    note             TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER fulfillments_updated_at BEFORE UPDATE ON checkout.fulfillments
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE checkout.fulfillment_items (
    id             UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    fulfillment_id UUID NOT NULL REFERENCES checkout.fulfillments(id),
    order_item_id  UUID NOT NULL REFERENCES checkout.order_items(id),
    quantity       INT  NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER fulfillment_items_updated_at BEFORE UPDATE ON checkout.fulfillment_items
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
