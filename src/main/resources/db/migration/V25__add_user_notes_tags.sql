ALTER TABLE auth.users
    ADD COLUMN admin_notes TEXT,
    ADD COLUMN tags        TEXT[] NOT NULL DEFAULT '{}';
