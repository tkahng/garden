CREATE TABLE checkout.order_templates (
    id         UUID         NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES auth.users(id) ON DELETE CASCADE,
    name       VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_order_templates_user_id ON checkout.order_templates (user_id);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON checkout.order_templates
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE checkout.order_template_items (
    id          UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES checkout.order_templates(id) ON DELETE CASCADE,
    variant_id  UUID NOT NULL REFERENCES catalog.product_variants(id),
    quantity    INT  NOT NULL CHECK (quantity >= 1),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE INDEX idx_order_template_items_template_id ON checkout.order_template_items (template_id);
