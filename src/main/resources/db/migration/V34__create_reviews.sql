CREATE TABLE catalog.product_reviews (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id  UUID        NOT NULL REFERENCES catalog.products(id),
    user_id     UUID        NOT NULL REFERENCES auth.users(id),
    rating      SMALLINT    NOT NULL CHECK (rating BETWEEN 1 AND 5),
    title       TEXT,
    body        TEXT,
    verified_purchase BOOLEAN NOT NULL DEFAULT FALSE,
    status      TEXT        NOT NULL DEFAULT 'PUBLISHED' CHECK (status IN ('PUBLISHED', 'HIDDEN')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE UNIQUE INDEX idx_product_reviews_user_product ON catalog.product_reviews (product_id, user_id);
CREATE INDEX idx_product_reviews_product_status ON catalog.product_reviews (product_id, status);

CREATE TRIGGER set_updated_at_product_reviews
    BEFORE UPDATE ON catalog.product_reviews
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
