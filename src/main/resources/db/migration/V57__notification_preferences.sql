CREATE TABLE auth.notification_preferences (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id           UUID        NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    notification_type VARCHAR(40) NOT NULL
                          CHECK (notification_type IN (
                              'ORDER_CONFIRMATION','ORDER_SHIPPED','ORDER_DELIVERED',
                              'ORDER_CANCELLED','QUOTE_UPDATE','MARKETING'
                          )),
    enabled           BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT uq_notification_pref UNIQUE (user_id, notification_type)
);

CREATE INDEX idx_notification_prefs_user_id ON auth.notification_preferences (user_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON auth.notification_preferences
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
