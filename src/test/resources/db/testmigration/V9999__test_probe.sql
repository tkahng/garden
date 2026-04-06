-- Test-only table for verifying BaseEntity behaviour.
-- Dropped automatically when the Testcontainer is destroyed.
CREATE TABLE shared.probe (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    label      TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON shared.probe
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
