ALTER TABLE storage.blob_objects
    ADD COLUMN alt   TEXT,
    ADD COLUMN title TEXT,
    ADD COLUMN width  INT,
    ADD COLUMN height INT;

INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000048', 'blob:update', 'blob', 'update', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r, auth.permissions p
WHERE r.name IN ('OWNER', 'MANAGER')
  AND p.name = 'blob:update'
ON CONFLICT DO NOTHING;
