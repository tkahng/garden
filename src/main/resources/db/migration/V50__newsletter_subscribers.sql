CREATE SCHEMA IF NOT EXISTS marketing;

CREATE TABLE marketing.newsletter_subscribers (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    email       TEXT        NOT NULL,
    source      TEXT,
    subscribed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    unsubscribed_at TIMESTAMPTZ,
    CONSTRAINT uq_newsletter_email UNIQUE (email)
);

CREATE INDEX idx_newsletter_subscribers_email ON marketing.newsletter_subscribers (email);
