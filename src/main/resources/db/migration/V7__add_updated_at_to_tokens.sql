-- tokens table was created without updated_at; add it to satisfy BaseEntity mapping.
ALTER TABLE tokens
    ADD COLUMN updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp();

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
