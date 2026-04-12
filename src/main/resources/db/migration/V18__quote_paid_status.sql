-- Add PAID to quote_requests status check constraint
ALTER TABLE quote.quote_requests
    DROP CONSTRAINT IF EXISTS quote_requests_status_check;

ALTER TABLE quote.quote_requests
    ADD CONSTRAINT quote_requests_status_check
        CHECK (status IN ('PENDING','ASSIGNED','DRAFT','SENT','ACCEPTED','PAID','REJECTED','EXPIRED','CANCELLED'));
