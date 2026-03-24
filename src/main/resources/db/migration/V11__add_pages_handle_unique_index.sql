-- Partial unique index: no two live (non-deleted) pages may share the same handle.
-- Soft-deleted pages (deleted_at IS NOT NULL) are excluded so their handles can be reused.
CREATE UNIQUE INDEX pages_handle_unique ON content.pages(handle) WHERE deleted_at IS NULL;
