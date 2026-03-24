-- catalog.collections
CREATE TABLE catalog.collections (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title             TEXT        NOT NULL,
    handle            TEXT        NOT NULL UNIQUE,
    description       TEXT,
    collection_type   TEXT        NOT NULL CHECK (collection_type IN ('MANUAL', 'AUTOMATED')),
    status            TEXT        NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE')),
    featured_image_id UUID,
    disjunctive       BOOLEAN     NOT NULL DEFAULT false,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_collections_handle     ON catalog.collections (handle);
CREATE INDEX idx_collections_status     ON catalog.collections (status);
CREATE INDEX idx_collections_deleted_at ON catalog.collections (deleted_at);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.collections
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- catalog.collection_rules
CREATE TABLE catalog.collection_rules (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    collection_id UUID        NOT NULL REFERENCES catalog.collections(id) ON DELETE CASCADE,
    field         TEXT        NOT NULL CHECK (field IN ('TAG')),
    operator      TEXT        NOT NULL CHECK (operator IN ('EQUALS', 'NOT_EQUALS', 'CONTAINS')),
    value         TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_collection_rules_collection_id ON catalog.collection_rules (collection_id);

-- catalog.collection_products
CREATE TABLE catalog.collection_products (
    id            UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    collection_id UUID        NOT NULL REFERENCES catalog.collections(id) ON DELETE CASCADE,
    product_id    UUID        NOT NULL REFERENCES catalog.products(id) ON DELETE CASCADE,
    position      INTEGER     NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (collection_id, product_id)
);

CREATE INDEX idx_collection_products_collection_id ON catalog.collection_products (collection_id);
CREATE INDEX idx_collection_products_product_id    ON catalog.collection_products (product_id);

-- Permissions
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000029', 'collection:read',    'collection', 'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000030', 'collection:write',   'collection', 'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000031', 'collection:publish', 'collection', 'publish', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000032', 'collection:delete',  'collection', 'delete',  clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name IN ('STAFF', 'MANAGER', 'OWNER')
  AND p.name IN ('collection:read', 'collection:write', 'collection:publish', 'collection:delete')
ON CONFLICT DO NOTHING;
