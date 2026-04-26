-- Contract-based pricing: price lists per company with per-variant entries

-- b2b.price_lists
CREATE TABLE b2b.price_lists (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    company_id  UUID        NOT NULL REFERENCES b2b.companies(id) ON DELETE CASCADE,
    name        TEXT        NOT NULL,
    currency    TEXT        NOT NULL DEFAULT 'USD',
    priority    INT         NOT NULL DEFAULT 0,
    starts_at   TIMESTAMPTZ,
    ends_at     TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT chk_price_list_dates CHECK (ends_at IS NULL OR starts_at IS NULL OR ends_at > starts_at)
);

CREATE INDEX idx_price_lists_company_id ON b2b.price_lists (company_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON b2b.price_lists
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- b2b.price_list_entries
CREATE TABLE b2b.price_list_entries (
    id              UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    price_list_id   UUID           NOT NULL REFERENCES b2b.price_lists(id) ON DELETE CASCADE,
    variant_id      UUID           NOT NULL REFERENCES catalog.product_variants(id) ON DELETE CASCADE,
    price           NUMERIC(19, 4) NOT NULL CHECK (price >= 0),
    min_qty         INT            NOT NULL DEFAULT 1 CHECK (min_qty >= 1),
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_price_list_entry UNIQUE (price_list_id, variant_id, min_qty)
);

CREATE INDEX idx_price_list_entries_list_id    ON b2b.price_list_entries (price_list_id);
CREATE INDEX idx_price_list_entries_variant_id ON b2b.price_list_entries (variant_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON b2b.price_list_entries
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- Permissions: IDs continue from V27 (last was 000...048)
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000051', 'price_list:read',   'price_list', 'read',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000052', 'price_list:write',  'price_list', 'write',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000053', 'price_list:delete', 'price_list', 'delete', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- MANAGER and STAFF get read; MANAGER gets write/delete; OWNER picks up all automatically
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'STAFF'
  AND p.name IN ('price_list:read')
ON CONFLICT DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN ('price_list:read', 'price_list:write', 'price_list:delete')
ON CONFLICT DO NOTHING;
