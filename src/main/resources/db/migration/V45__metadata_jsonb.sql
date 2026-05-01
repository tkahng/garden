ALTER TABLE catalog.products ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}';
ALTER TABLE checkout.orders  ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}';
ALTER TABLE b2b.companies    ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}';
ALTER TABLE auth.users       ADD COLUMN metadata JSONB NOT NULL DEFAULT '{}';
