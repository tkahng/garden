-- Roles
INSERT INTO roles (id, name, description, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000001', 'CUSTOMER', 'Storefront customer', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000002', 'STAFF', 'Admin staff — manage products, inventory, content', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000003', 'MANAGER', 'Admin manager — staff + orders, discounts, staff accounts', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000004', 'OWNER', 'Full access including settings, roles, permissions', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- Permissions
INSERT INTO permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000011', 'product:read',      'product',   'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000012', 'product:write',     'product',   'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000013', 'product:publish',   'product',   'publish', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000014', 'product:delete',    'product',   'delete',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000015', 'inventory:read',    'inventory', 'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000016', 'inventory:write',   'inventory', 'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000017', 'content:read',      'content',   'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000018', 'content:write',     'content',   'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000019', 'content:publish',   'content',   'publish', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000020', 'content:delete',    'content',   'delete',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000021', 'user:read',         'user',      'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000022', 'user:write',        'user',      'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000023', 'staff:manage',      'staff',     'manage',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000024', 'iam:manage',        'iam',       'manage',  clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- CUSTOMER: storefront read only
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'CUSTOMER' AND p.name IN ('product:read', 'content:read')
ON CONFLICT DO NOTHING;

-- STAFF: products + inventory + content (no delete), user:read
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'STAFF'
  AND p.name IN ('product:read', 'product:write', 'product:publish',
                 'inventory:read', 'inventory:write',
                 'content:read', 'content:write', 'content:publish',
                 'user:read')
ON CONFLICT DO NOTHING;

-- MANAGER: staff + deletes + user:write + staff:manage
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN ('product:read', 'product:write', 'product:publish', 'product:delete',
                 'inventory:read', 'inventory:write',
                 'content:read', 'content:write', 'content:publish', 'content:delete',
                 'user:read', 'user:write', 'staff:manage')
ON CONFLICT DO NOTHING;

-- OWNER: all permissions (JWT minting expands to all at runtime, but also set here for completeness)
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name = 'OWNER'
ON CONFLICT DO NOTHING;
