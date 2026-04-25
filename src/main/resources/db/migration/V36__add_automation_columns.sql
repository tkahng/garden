-- Abandoned cart recovery: track when a reminder was last sent per cart
ALTER TABLE checkout.carts ADD COLUMN IF NOT EXISTS abandoned_reminder_sent_at TIMESTAMPTZ;

-- Low-stock alerts: track when an alert was last sent per inventory level
ALTER TABLE inventory.inventory_levels ADD COLUMN IF NOT EXISTS low_stock_alerted_at TIMESTAMPTZ;
