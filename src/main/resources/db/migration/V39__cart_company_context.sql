-- Add company context to cart for B2B price list resolution

ALTER TABLE checkout.carts
    ADD COLUMN company_id UUID REFERENCES b2b.companies(id) ON DELETE SET NULL;

CREATE INDEX idx_carts_company_id ON checkout.carts (company_id);
