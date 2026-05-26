-- Scheduler query optimization: finding pending/unpaid orders with a Stripe session
-- is a hot path for the payment-reconciliation scheduler.
CREATE INDEX IF NOT EXISTS idx_orders_status_stripe_session_created
    ON checkout.orders (status, created_at DESC)
    WHERE stripe_session_id IS NOT NULL;

-- Cart lookup by user + status (e.g. finding active carts for a user).
CREATE INDEX IF NOT EXISTS idx_carts_user_status
    ON checkout.carts (user_id, status);
