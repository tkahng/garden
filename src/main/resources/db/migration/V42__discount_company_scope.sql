ALTER TABLE checkout.discounts
    ADD COLUMN company_id UUID REFERENCES b2b.companies(id) ON DELETE SET NULL;

CREATE INDEX idx_discounts_company_id ON checkout.discounts (company_id)
    WHERE company_id IS NOT NULL;
