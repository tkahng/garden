CREATE TABLE storage.blob_objects (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    key          TEXT        NOT NULL UNIQUE,
    filename     TEXT        NOT NULL,
    content_type TEXT        NOT NULL,
    size         BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON storage.blob_objects
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000025', 'blob:upload', 'blob', 'upload', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000026', 'blob:delete', 'blob', 'delete', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM auth.roles r, auth.permissions p
WHERE r.name IN ('OWNER', 'MANAGER')
  AND p.name IN ('blob:upload', 'blob:delete')
ON CONFLICT DO NOTHING;
