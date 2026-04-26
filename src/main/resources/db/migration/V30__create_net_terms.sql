-- Net terms & credit management

-- Expand order status to include INVOICED
ALTER TABLE checkout.orders DROP CONSTRAINT orders_status_check;
ALTER TABLE checkout.orders ADD CONSTRAINT orders_status_check
    CHECK (status IN ('PENDING_PAYMENT', 'PAID', 'CANCELLED', 'REFUNDED',
                      'PARTIALLY_FULFILLED', 'FULFILLED', 'INVOICED'));

-- b2b.credit_accounts — one per company
CREATE TABLE b2b.credit_accounts (
    id                   UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id           UUID           NOT NULL UNIQUE REFERENCES b2b.companies(id) ON DELETE CASCADE,
    credit_limit         NUMERIC(19, 4) NOT NULL CHECK (credit_limit > 0),
    payment_terms_days   INT            NOT NULL DEFAULT 30 CHECK (payment_terms_days > 0),
    currency             TEXT           NOT NULL DEFAULT 'USD',
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON b2b.credit_accounts
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- b2b.invoices
CREATE TABLE b2b.invoices (
    id           UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id   UUID           NOT NULL REFERENCES b2b.companies(id),
    order_id     UUID           NOT NULL UNIQUE REFERENCES checkout.orders(id),
    quote_id     UUID           REFERENCES quote.quote_requests(id),
    status       TEXT           NOT NULL DEFAULT 'ISSUED'
                                CHECK (status IN ('ISSUED', 'PARTIAL', 'PAID', 'OVERDUE', 'VOID')),
    total_amount NUMERIC(19, 4) NOT NULL CHECK (total_amount >= 0),
    paid_amount  NUMERIC(19, 4) NOT NULL DEFAULT 0 CHECK (paid_amount >= 0),
    currency     TEXT           NOT NULL DEFAULT 'USD',
    issued_at    TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    due_at       TIMESTAMPTZ    NOT NULL,
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_invoices_company_id ON b2b.invoices (company_id);
CREATE INDEX idx_invoices_status     ON b2b.invoices (status);
CREATE INDEX idx_invoices_due_at     ON b2b.invoices (due_at);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON b2b.invoices
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- b2b.invoice_payments — partial payment records
CREATE TABLE b2b.invoice_payments (
    id                UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    invoice_id        UUID           NOT NULL REFERENCES b2b.invoices(id) ON DELETE CASCADE,
    amount            NUMERIC(19, 4) NOT NULL CHECK (amount > 0),
    payment_reference TEXT,
    notes             TEXT,
    paid_at           TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_invoice_payments_invoice_id ON b2b.invoice_payments (invoice_id);

-- Permissions: IDs continue from V28 (last was 053)
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000054', 'credit_account:read',   'credit_account', 'read',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000055', 'credit_account:write',  'credit_account', 'write',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000056', 'invoice:read',          'invoice',        'read',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000057', 'invoice:write',         'invoice',        'write',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000058', 'invoice:delete',        'invoice',        'delete', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'STAFF'
  AND p.name IN ('credit_account:read', 'invoice:read')
ON CONFLICT DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN ('credit_account:read', 'credit_account:write',
                 'invoice:read', 'invoice:write', 'invoice:delete')
ON CONFLICT DO NOTHING;
