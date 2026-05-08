-- Products full-text search
ALTER TABLE catalog.products
    ADD COLUMN search_vector TSVECTOR
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(vendor, '')), 'B') ||
            setweight(to_tsvector('english', coalesce(product_type, '')), 'B') ||
            setweight(to_tsvector('english', coalesce(description, '')), 'C')
        ) STORED;

CREATE INDEX idx_products_search ON catalog.products USING GIN (search_vector);

-- Collections full-text search
ALTER TABLE catalog.collections
    ADD COLUMN search_vector TSVECTOR
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(description, '')), 'C')
        ) STORED;

CREATE INDEX idx_collections_search ON catalog.collections USING GIN (search_vector);

-- Articles full-text search
ALTER TABLE content.articles
    ADD COLUMN search_vector TSVECTOR
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(excerpt, '')), 'B') ||
            setweight(to_tsvector('english', coalesce(body, '')), 'C')
        ) STORED;

CREATE INDEX idx_articles_search ON content.articles USING GIN (search_vector);

-- Pages full-text search
ALTER TABLE content.pages
    ADD COLUMN search_vector TSVECTOR
        GENERATED ALWAYS AS (
            setweight(to_tsvector('english', coalesce(title, '')), 'A') ||
            setweight(to_tsvector('english', coalesce(body, '')), 'C')
        ) STORED;

CREATE INDEX idx_pages_search ON content.pages USING GIN (search_vector);
