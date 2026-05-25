-- Configurable per-company approval rules (replaces hardcoded spending_limit gate)
CREATE TABLE IF NOT EXISTS b2b.company_approval_rules (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    company_id    UUID        NOT NULL REFERENCES b2b.companies(id) ON DELETE CASCADE,
    name          TEXT        NOT NULL,
    threshold_amount NUMERIC(19,4) NOT NULL,
    required_role TEXT        NOT NULL CHECK (required_role IN ('MANAGER','OWNER')),
    is_active     BOOLEAN     NOT NULL DEFAULT true,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_company_approval_rules_company_id
    ON b2b.company_approval_rules (company_id);

-- Per-quote approval pendencies: tracks which rules have been satisfied
CREATE TABLE IF NOT EXISTS b2b.quote_approval_pendencies (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    quote_id    UUID        NOT NULL REFERENCES quote.quote_requests(id) ON DELETE CASCADE,
    rule_id     UUID        NOT NULL REFERENCES b2b.company_approval_rules(id) ON DELETE CASCADE,
    resolved_by UUID        REFERENCES auth.users(id),
    resolved_at TIMESTAMPTZ,
    action      TEXT        CHECK (action IN ('APPROVED','REJECTED')),
    rejection_reason TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX IF NOT EXISTS idx_quote_approval_pendencies_quote_id
    ON b2b.quote_approval_pendencies (quote_id);
