-- RMA: return request workflow

CREATE TABLE checkout.return_requests (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    order_id    UUID        NOT NULL REFERENCES checkout.orders(id),
    user_id     UUID        REFERENCES auth.users(id),
    reason      VARCHAR(30) NOT NULL
                    CHECK (reason IN ('DAMAGED','WRONG_ITEM','NOT_AS_DESCRIBED','CHANGED_MIND','OTHER')),
    notes       TEXT,
    resolution  VARCHAR(20) NOT NULL DEFAULT 'REFUND'
                    CHECK (resolution IN ('REFUND','EXCHANGE','STORE_CREDIT')),
    status      VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                    CHECK (status IN ('PENDING','APPROVED','REJECTED','COMPLETED')),
    staff_notes TEXT,
    resolved_by UUID        REFERENCES auth.users(id),
    resolved_at TIMESTAMPTZ,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_return_requests_order_id ON checkout.return_requests (order_id);
CREATE INDEX idx_return_requests_user_id  ON checkout.return_requests (user_id);
CREATE INDEX idx_return_requests_status   ON checkout.return_requests (status);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON checkout.return_requests
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE checkout.return_request_items (
    id                UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    return_request_id UUID NOT NULL REFERENCES checkout.return_requests(id) ON DELETE CASCADE,
    order_item_id     UUID NOT NULL REFERENCES checkout.order_items(id),
    quantity          INT  NOT NULL CHECK (quantity >= 1),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_return_item UNIQUE (return_request_id, order_item_id)
);

CREATE INDEX idx_return_request_items_rr_id ON checkout.return_request_items (return_request_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON checkout.return_request_items
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

-- Permissions (IDs continue after 000...061 used in V37)
INSERT INTO auth.permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000062', 'return:read',   'return', 'read',   clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000063', 'return:write',  'return', 'write',  clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name IN ('STAFF','MANAGER') AND p.name = 'return:read'
ON CONFLICT DO NOTHING;

INSERT INTO auth.role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM auth.roles r, auth.permissions p
WHERE r.name = 'MANAGER' AND p.name = 'return:write'
ON CONFLICT DO NOTHING;
