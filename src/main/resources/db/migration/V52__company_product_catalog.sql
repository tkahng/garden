CREATE TABLE IF NOT EXISTS b2b.company_product_catalogs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id UUID NOT NULL REFERENCES b2b.companies(id) ON DELETE CASCADE,
    product_id UUID NOT NULL REFERENCES catalog.products(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (company_id, product_id)
);
