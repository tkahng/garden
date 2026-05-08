ALTER TABLE catalog.product_variants
    ADD COLUMN IF NOT EXISTS min_order_qty INT NOT NULL DEFAULT 1;
