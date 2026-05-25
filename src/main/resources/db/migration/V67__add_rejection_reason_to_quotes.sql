ALTER TABLE quote.quote_requests
    ADD COLUMN IF NOT EXISTS rejection_reason TEXT;
