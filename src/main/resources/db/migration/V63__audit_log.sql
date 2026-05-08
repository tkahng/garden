CREATE TABLE shared.audit_log (
    id          UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    actor_id    UUID,
    actor_email TEXT,
    action      TEXT        NOT NULL,
    entity_type TEXT        NOT NULL,
    entity_id   TEXT,
    before_json JSONB,
    after_json  JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_audit_log_actor    ON shared.audit_log (actor_id);
CREATE INDEX idx_audit_log_entity   ON shared.audit_log (entity_type, entity_id);
CREATE INDEX idx_audit_log_created  ON shared.audit_log (created_at DESC);
