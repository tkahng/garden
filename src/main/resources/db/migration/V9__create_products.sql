CREATE TABLE catalog.products (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title             TEXT        NOT NULL,
    description       TEXT,
    handle            TEXT        NOT NULL UNIQUE,
    vendor            TEXT,
    product_type      TEXT,
    status            TEXT        NOT NULL DEFAULT 'DRAFT',
    featured_image_id UUID,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.products
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.product_images (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id UUID        NOT NULL REFERENCES catalog.products(id),
    blob_id    UUID        NOT NULL REFERENCES storage.blob_objects(id),
    alt_text   TEXT,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.product_images
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.product_options (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id UUID        NOT NULL REFERENCES catalog.products(id),
    name       TEXT        NOT NULL,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.product_options
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.product_option_values (
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    option_id UUID        NOT NULL REFERENCES catalog.product_options(id),
    label     TEXT        NOT NULL,
    position  INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.product_option_values
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.product_variants (
    id               UUID          NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id       UUID          NOT NULL REFERENCES catalog.products(id),
    title            TEXT          NOT NULL,
    sku              TEXT          UNIQUE,
    barcode          TEXT,
    price            NUMERIC(19,4) NOT NULL,
    compare_at_price NUMERIC(19,4),
    weight           NUMERIC(10,4),
    weight_unit      TEXT,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ   NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ   NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.product_variants
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.variant_option_values (
    variant_id      UUID NOT NULL REFERENCES catalog.product_variants(id),
    option_value_id UUID NOT NULL REFERENCES catalog.product_option_values(id),
    PRIMARY KEY (variant_id, option_value_id)
);

CREATE TABLE catalog.product_tags (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON catalog.product_tags
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE catalog.product_product_tags (
    product_id UUID NOT NULL REFERENCES catalog.products(id),
    tag_id     UUID NOT NULL REFERENCES catalog.product_tags(id),
    PRIMARY KEY (product_id, tag_id)
);

CREATE TABLE inventory.inventory_items (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    variant_id        UUID        NOT NULL UNIQUE REFERENCES catalog.product_variants(id),
    requires_shipping BOOLEAN     NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON inventory.inventory_items
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();
