-- Tax exemption flag on companies
ALTER TABLE b2b.companies
    ADD COLUMN tax_exempt BOOLEAN NOT NULL DEFAULT FALSE;

-- Company context + tax exemption on orders
ALTER TABLE checkout.orders
    ADD COLUMN company_id UUID REFERENCES b2b.companies(id) ON DELETE SET NULL,
    ADD COLUMN tax_exempt BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX idx_orders_company_id ON checkout.orders (company_id) WHERE company_id IS NOT NULL;
