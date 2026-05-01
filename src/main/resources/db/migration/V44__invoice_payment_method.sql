ALTER TABLE b2b.invoice_payments
    ADD COLUMN payment_method VARCHAR(30) NOT NULL DEFAULT 'STRIPE';
