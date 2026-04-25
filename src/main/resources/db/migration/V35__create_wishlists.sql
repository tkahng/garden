CREATE TABLE catalog.wishlists (
    id         UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL UNIQUE REFERENCES auth.users(id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TABLE catalog.wishlist_items (
    id          UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    wishlist_id UUID        NOT NULL REFERENCES catalog.wishlists(id) ON DELETE CASCADE,
    product_id  UUID        NOT NULL REFERENCES catalog.products(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    UNIQUE (wishlist_id, product_id)
);

CREATE TRIGGER set_updated_at_wishlist_items
    BEFORE UPDATE ON catalog.wishlist_items
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE INDEX idx_wishlist_items_wishlist ON catalog.wishlist_items (wishlist_id);

CREATE TRIGGER set_updated_at_wishlists
    BEFORE UPDATE ON catalog.wishlists
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
