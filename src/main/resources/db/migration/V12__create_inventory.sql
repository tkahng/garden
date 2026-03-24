-- New permissions for location management
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000027', 'location:read',  'location', 'read',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000028', 'location:write', 'location', 'write', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- STAFF, MANAGER, OWNER receive both location permissions
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name IN ('STAFF', 'MANAGER', 'OWNER')
  AND p.name IN ('location:read', 'location:write')
ON CONFLICT DO NOTHING;

-- Extend product_variants with fulfillment fields
ALTER TABLE catalog.product_variants
    ADD COLUMN fulfillment_type TEXT NOT NULL DEFAULT 'IN_STOCK',
    ADD COLUMN inventory_policy TEXT NOT NULL DEFAULT 'DENY',
    ADD COLUMN lead_time_days   INT  NOT NULL DEFAULT 0;

-- Locations table
CREATE TABLE inventory.locations (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL,
    address    TEXT,
    is_active  BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON inventory.locations
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- Inventory levels (qty per inventory item x location)
CREATE TABLE inventory.inventory_levels (
    id                 UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    inventory_item_id  UUID        NOT NULL REFERENCES inventory.inventory_items(id) ON DELETE CASCADE,
    location_id        UUID        NOT NULL REFERENCES inventory.locations(id) ON DELETE CASCADE,
    quantity_on_hand   INT         NOT NULL DEFAULT 0,
    quantity_committed INT         NOT NULL DEFAULT 0,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (inventory_item_id, location_id)
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON inventory.inventory_levels
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- Inventory transactions (immutable ledger — no updated_at)
CREATE TABLE inventory.inventory_transactions (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    inventory_item_id UUID        NOT NULL REFERENCES inventory.inventory_items(id) ON DELETE RESTRICT,
    location_id       UUID        NOT NULL REFERENCES inventory.locations(id) ON DELETE RESTRICT,
    quantity          INT         NOT NULL,
    reason            TEXT        NOT NULL,
    note              TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
