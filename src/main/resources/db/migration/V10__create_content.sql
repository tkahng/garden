CREATE TABLE content.pages (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title            TEXT        NOT NULL,
    handle           TEXT        NOT NULL,
    body             TEXT,
    status           TEXT        NOT NULL DEFAULT 'DRAFT',
    meta_title       TEXT,
    meta_description TEXT,
    published_at     TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content.pages
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE content.blogs (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title      TEXT        NOT NULL,
    handle     TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content.blogs
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE content.articles (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    blog_id           UUID        NOT NULL REFERENCES content.blogs(id) ON DELETE CASCADE,
    title             TEXT        NOT NULL,
    handle            TEXT        NOT NULL,
    body              TEXT,
    excerpt           TEXT,
    author_id         UUID        REFERENCES auth.users(id) ON DELETE SET NULL,
    author_name       TEXT,
    status            TEXT        NOT NULL DEFAULT 'DRAFT',
    featured_image_id UUID,
    meta_title        TEXT,
    meta_description  TEXT,
    published_at      TIMESTAMPTZ,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content.articles
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE content.article_images (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    article_id UUID        NOT NULL REFERENCES content.articles(id) ON DELETE CASCADE,
    blob_id    UUID        NOT NULL REFERENCES storage.blob_objects(id),
    alt_text   TEXT,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content.article_images
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE content.content_tags (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content.content_tags
    FOR EACH ROW EXECUTE FUNCTION shared.set_updated_at();

CREATE TABLE content.article_content_tags (
    article_id     UUID NOT NULL REFERENCES content.articles(id),
    content_tag_id UUID NOT NULL REFERENCES content.content_tags(id),
    PRIMARY KEY (article_id, content_tag_id)
);
