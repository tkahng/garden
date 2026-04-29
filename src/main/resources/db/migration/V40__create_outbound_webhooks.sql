-- Outbound webhooks: allow external systems to subscribe to order/fulfillment/invoice events

CREATE SCHEMA IF NOT EXISTS webhook;

CREATE TABLE webhook.endpoints (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    url         TEXT        NOT NULL,
    secret      TEXT        NOT NULL,
    description TEXT,
    events      TEXT[]      NOT NULL DEFAULT '{}',
    is_active   BOOLEAN     NOT NULL DEFAULT true,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON webhook.endpoints
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE webhook.deliveries (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    endpoint_id       UUID        NOT NULL REFERENCES webhook.endpoints(id) ON DELETE CASCADE,
    event_type        TEXT        NOT NULL,
    payload           JSONB       NOT NULL,
    status            TEXT        NOT NULL DEFAULT 'PENDING'
                                  CHECK (status IN ('PENDING', 'SUCCESS', 'FAILED')),
    attempt_count     INT         NOT NULL DEFAULT 0,
    last_attempted_at TIMESTAMPTZ,
    next_retry_at     TIMESTAMPTZ,
    http_status       INT,
    response_body     TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_deliveries_endpoint_id  ON webhook.deliveries (endpoint_id);
CREATE INDEX idx_deliveries_pending      ON webhook.deliveries (next_retry_at) WHERE status = 'PENDING';
CREATE INDEX idx_deliveries_retry        ON webhook.deliveries (next_retry_at) WHERE status = 'FAILED' AND next_retry_at IS NOT NULL;

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON webhook.deliveries
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- Permissions
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000059', 'webhook:read',   'webhook', 'read',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000060', 'webhook:write',  'webhook', 'write',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000061', 'webhook:delete', 'webhook', 'delete', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'MANAGER'
  AND p.name IN ('webhook:read', 'webhook:write', 'webhook:delete')
ON CONFLICT DO NOTHING;
