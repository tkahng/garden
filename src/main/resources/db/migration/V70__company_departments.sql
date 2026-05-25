-- Company departments (tree structure for org hierarchy)
CREATE TABLE IF NOT EXISTS b2b.departments (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id    UUID        NOT NULL REFERENCES b2b.companies(id) ON DELETE CASCADE,
    parent_id     UUID        REFERENCES b2b.departments(id) ON DELETE SET NULL,
    name          TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (company_id, name, parent_id)
);

CREATE INDEX IF NOT EXISTS idx_departments_company_id
    ON b2b.departments (company_id);

CREATE INDEX IF NOT EXISTS idx_departments_parent_id
    ON b2b.departments (parent_id);

-- Add department assignment to memberships
ALTER TABLE b2b.company_memberships
    ADD COLUMN IF NOT EXISTS department_id UUID REFERENCES b2b.departments(id) ON DELETE SET NULL;
