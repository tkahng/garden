CREATE TABLE auth.rate_limit_attempts (
    ip           TEXT        NOT NULL,
    attempted_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_rla_ip_attempted_at ON auth.rate_limit_attempts (ip, attempted_at);
