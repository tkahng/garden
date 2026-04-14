-- New permissions for admin features
-- IDs continue from V16 (last was 00000000-0000-7000-8000-000000000036)
-- blob:upload (25) and blob:delete (26) were added in V8
-- order:read (33) and order:write (34) were added in V14
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000037', 'blob:read',        'blob',      'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000038', 'discount:read',    'discount',  'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000039', 'discount:write',   'discount',  'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000040', 'discount:delete',  'discount',  'delete',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000041', 'shipping:read',    'shipping',  'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000042', 'shipping:write',   'shipping',  'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000043', 'shipping:delete',  'shipping',  'delete',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000044', 'gift_card:read',   'gift_card', 'read',    clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000045', 'gift_card:write',  'gift_card', 'write',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000046', 'gift_card:delete', 'gift_card', 'delete',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000047', 'stats:read',       'stats',     'read',    clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- STAFF: blob:read + stats visibility
-- (blob:upload, blob:delete already assigned to MANAGER/OWNER in V8)
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'STAFF'
  AND p.name IN ('blob:upload', 'blob:delete', 'blob:read', 'stats:read')
ON CONFLICT DO NOTHING;

-- MANAGER: discounts, shipping, gift cards, stats, blob
-- (order:read, order:write already assigned to MANAGER in V14)
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN (
      'blob:read', 'stats:read',
      'discount:read', 'discount:write', 'discount:delete',
      'shipping:read', 'shipping:write', 'shipping:delete',
      'gift_card:read', 'gift_card:write', 'gift_card:delete'
  )
ON CONFLICT DO NOTHING;

-- OWNER: picks up all permissions automatically via IamService.loadPermissionsForUser()
-- (permissionRepo.findAllNames() query — no role_permissions insert needed)
