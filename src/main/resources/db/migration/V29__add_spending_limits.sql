-- Spending limits on company memberships + approval tracking on quotes

-- Per-member spending limit; NULL = unlimited
ALTER TABLE b2b.company_memberships
    ADD COLUMN spending_limit NUMERIC(19, 4) CHECK (spending_limit > 0);

-- Approval tracking on quote requests
ALTER TABLE quote.quote_requests
    ADD COLUMN approver_id  UUID REFERENCES auth.users(id),
    ADD COLUMN approved_at  TIMESTAMPTZ;

-- Expand the status check to include PENDING_APPROVAL
ALTER TABLE quote.quote_requests
    DROP CONSTRAINT quote_requests_status_check;

ALTER TABLE quote.quote_requests
    ADD CONSTRAINT quote_requests_status_check
    CHECK (status IN (
        'PENDING', 'ASSIGNED', 'DRAFT', 'SENT',
        'ACCEPTED', 'PAID', 'REJECTED', 'EXPIRED', 'CANCELLED',
        'PENDING_APPROVAL'
    ));
