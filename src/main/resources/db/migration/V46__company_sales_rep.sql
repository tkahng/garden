ALTER TABLE b2b.companies
    ADD COLUMN sales_rep_user_id UUID REFERENCES auth.users(id) ON DELETE SET NULL;

CREATE INDEX idx_companies_sales_rep ON b2b.companies(sales_rep_user_id)
    WHERE sales_rep_user_id IS NOT NULL;
