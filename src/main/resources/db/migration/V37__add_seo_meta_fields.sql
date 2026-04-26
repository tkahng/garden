ALTER TABLE catalog.products
    ADD COLUMN meta_title       VARCHAR(255),
    ADD COLUMN meta_description TEXT;

ALTER TABLE catalog.collections
    ADD COLUMN meta_title       VARCHAR(255),
    ADD COLUMN meta_description TEXT;
