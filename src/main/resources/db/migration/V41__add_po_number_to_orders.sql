-- Purchase order number: allows B2B buyers to attach their procurement PO reference to an order

ALTER TABLE checkout.orders
    ADD COLUMN po_number TEXT;

CREATE INDEX idx_orders_po_number ON checkout.orders (po_number) WHERE po_number IS NOT NULL;
