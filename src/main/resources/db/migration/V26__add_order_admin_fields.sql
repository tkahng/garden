ALTER TABLE checkout.orders
    ADD COLUMN admin_notes      TEXT,
    ADD COLUMN shipping_address JSONB;
