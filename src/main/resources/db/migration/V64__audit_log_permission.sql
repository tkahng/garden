INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000064', 'audit:read', 'audit', 'read', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- Grant to MANAGER and OWNER (OWNER already gets all permissions via the code path)
INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'MANAGER' AND p.name = 'audit:read'
ON CONFLICT DO NOTHING;
