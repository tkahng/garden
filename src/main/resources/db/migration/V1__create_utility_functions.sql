-- Trigger function: sets updated_at to clock_timestamp() on every row update.
-- clock_timestamp() returns the actual wall-clock time at the moment of the call,
-- unlike now() which returns the transaction start time (same for all rows in a
-- transaction). This is critical for @Transactional integration tests where all
-- inserts share one transaction — clock_timestamp() preserves ordering.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
