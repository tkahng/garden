CREATE TABLE checkout.processed_stripe_events (
    event_id     TEXT        NOT NULL PRIMARY KEY,
    processed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_pse_processed_at ON checkout.processed_stripe_events (processed_at);
