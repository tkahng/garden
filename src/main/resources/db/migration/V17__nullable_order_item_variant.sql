-- Allow order items without a variant (for custom quote line items)
ALTER TABLE checkout.order_items
    ALTER COLUMN variant_id DROP NOT NULL;
