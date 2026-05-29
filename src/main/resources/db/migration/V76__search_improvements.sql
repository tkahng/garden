-- Enable trigram extension for fuzzy/partial search
CREATE EXTENSION IF NOT EXISTS pg_trgm;

-- Trigram index on product title for fuzzy matching
CREATE INDEX idx_products_title_trgm ON catalog.products USING GIN (title gin_trgm_ops);

-- Trigram index on variant SKU for exact and partial SKU search
CREATE INDEX idx_variants_sku_trgm ON catalog.product_variants USING GIN (sku gin_trgm_ops);
