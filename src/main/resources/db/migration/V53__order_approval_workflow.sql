-- Order approval workflow for B2B cart checkout

-- Expand order status to include PENDING_APPROVAL and DRAFT (missing from prior migrations)
ALTER TABLE checkout.orders DROP CONSTRAINT orders_status_check;
ALTER TABLE checkout.orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('DRAFT', 'PENDING_PAYMENT', 'PENDING_APPROVAL', 'PAID', 'CANCELLED',
                      'REFUNDED', 'PARTIALLY_FULFILLED', 'FULFILLED', 'INVOICED'));

-- Approval tracking on orders
ALTER TABLE checkout.orders
    ADD COLUMN approver_id UUID,
    ADD COLUMN approved_at TIMESTAMPTZ;
