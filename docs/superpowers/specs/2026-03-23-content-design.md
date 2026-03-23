# Content Domain Design Spec

**Date:** 2026-03-23
**Project:** `io.k2dv.garden`
**Domain:** `content`

---

## Goal

Build a content management domain covering static pages, blogs, and articles. Navigation menus are deferred to a future iteration.

---

## Architecture

Package-by-feature under `io.k2dv.garden.content`. Two services own distinct sub-domains: `PageService` owns pages, `ArticleService` owns blogs + articles + article images + content tags. All repositories extend `JpaSpecificationExecutor<T>` for `Specification`-based filtering. All list endpoints use offset pagination via Spring Data `Pageable` and return `PagedResult<T>`.

**Tech Stack:** Spring Boot 4.x, Spring Data JPA (`JpaSpecificationExecutor`), Hibernate 6, Flyway, Lombok, PostgreSQL, Testcontainers (IT), MockMvc (slice tests).

---

## Section 1: Data Model

### V10 Migration (DDL only)

All content permissions (`content:read`, `content:write`, `content:publish`, `content:delete`) are already seeded in V6. V10 contains no `INSERT` statements.

Every table with `updated_at` requires a `CREATE TRIGGER set_updated_at BEFORE UPDATE ON <table> FOR EACH ROW EXECUTE FUNCTION set_updated_at();` — consistent with all prior migrations.

V10 also creates two dedicated trigger functions for full-text search:

```sql
-- pages
CREATE OR REPLACE FUNCTION pages_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector := to_tsvector('english',
    coalesce(NEW.title, '') || ' ' ||
    coalesce(regexp_replace(NEW.body, '<[^>]+>', ' ', 'g'), '')
  );
  RETURN NEW;
END;$$ LANGUAGE plpgsql;
CREATE TRIGGER pages_search_vector_trigger
  BEFORE INSERT OR UPDATE ON pages
  FOR EACH ROW EXECUTE FUNCTION pages_search_vector_update();

-- articles
CREATE OR REPLACE FUNCTION articles_search_vector_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector := to_tsvector('english',
    coalesce(NEW.title, '') || ' ' ||
    coalesce(NEW.excerpt, '') || ' ' ||
    coalesce(regexp_replace(NEW.body, '<[^>]+>', ' ', 'g'), '')
  );
  RETURN NEW;
END;$$ LANGUAGE plpgsql;
CREATE TRIGGER articles_search_vector_trigger
  BEFORE INSERT OR UPDATE ON articles
  FOR EACH ROW EXECUTE FUNCTION articles_search_vector_update();
```

GIN indexes for fast full-text lookup:
```sql
CREATE INDEX pages_search_vector_idx    ON pages    USING gin(search_vector);
CREATE INDEX articles_search_vector_idx ON articles USING gin(search_vector);
```

### Tables

#### `pages`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` default; JPA generates UUIDv7 via `@UuidGenerator` (same as V9 pattern) |
| `title` | TEXT NOT NULL | |
| `handle` | TEXT NOT NULL | URL slug; uniqueness enforced at service layer among non-deleted (no DB UNIQUE — same pattern as product handles with soft delete) |
| `body` | TEXT | HTML body |
| `status` | TEXT NOT NULL DEFAULT 'DRAFT' | `DRAFT` or `PUBLISHED` |
| `meta_title` | TEXT | |
| `meta_description` | TEXT | |
| `published_at` | TIMESTAMPTZ | Set when status → PUBLISHED; cleared when → DRAFT |
| `deleted_at` | TIMESTAMPTZ | Soft delete |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |
| `search_vector` | TSVECTOR | Trigger-managed; covers `title` + HTML-stripped `body`; populated by `pages_search_vector_update` trigger (BEFORE INSERT OR UPDATE) |

Handle uniqueness: service checks `existsByHandleAndDeletedAtIsNull` before create, and `existsByHandleAndDeletedAtIsNullAndIdNot` before update. Throws `ConflictException` on collision.

#### `blogs`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` default |
| `title` | TEXT NOT NULL | |
| `handle` | TEXT NOT NULL UNIQUE | Globally unique; DB-level UNIQUE is safe since blogs have no soft delete |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |

No soft delete on blogs — deleting a blog hard-deletes and cascades to its articles (via `ON DELETE CASCADE` on `articles.blog_id`).

#### `articles`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` default |
| `blog_id` | UUID NOT NULL FK→blogs ON DELETE CASCADE | |
| `title` | TEXT NOT NULL | |
| `handle` | TEXT NOT NULL | Unique within blog among non-deleted; enforced at service layer |
| `body` | TEXT | HTML body |
| `excerpt` | TEXT | |
| `author_id` | UUID FK→users ON DELETE SET NULL | Nullable — set to NULL when author user is deleted |
| `author_name` | TEXT | Snapshot of `firstName + " " + lastName` at time of publish; never updated after first publish |
| `status` | TEXT NOT NULL DEFAULT 'DRAFT' | `DRAFT` or `PUBLISHED` |
| `featured_image_id` | UUID | Plain UUID column, no FK (avoids circular dependency with `article_images`) |
| `meta_title` | TEXT | |
| `meta_description` | TEXT | |
| `published_at` | TIMESTAMPTZ | Set when status → PUBLISHED; cleared when → DRAFT |
| `deleted_at` | TIMESTAMPTZ | Soft delete |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |
| `search_vector` | TSVECTOR | Trigger-managed; covers `title` + `excerpt` + HTML-stripped `body`; populated by `articles_search_vector_update` trigger (BEFORE INSERT OR UPDATE) |

Handle uniqueness: service checks uniqueness within the same `blog_id` among non-deleted articles. Throws `ConflictException` on collision. Two articles in different blogs may share the same handle.

#### `article_images`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` default |
| `article_id` | UUID NOT NULL FK→articles ON DELETE CASCADE | |
| `blob_id` | UUID NOT NULL FK→blob_objects | |
| `alt_text` | TEXT | |
| `position` | INT NOT NULL | Display order |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |

#### `content_tags`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` default |
| `name` | TEXT NOT NULL UNIQUE | |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |

Orphan tags (tags no longer referenced by any article) are intentionally retained — they form a shared tag pool for future reuse, consistent with the find-or-create pattern.

#### `article_content_tags` (join table)

| Column | Type | Notes |
|---|---|---|
| `article_id` | UUID NOT NULL FK→articles | |
| `content_tag_id` | UUID NOT NULL FK→content_tags | |
| PRIMARY KEY | `(article_id, content_tag_id)` | |

No `id`, `created_at`, `updated_at`, or trigger on the join table.

### JPA Entity Notes

- All entities extend `BaseEntity` (`id` UUIDv7, `createdAt`, `updatedAt` via `@Generated`)
- `Article.featuredImageId` is a plain `@Column` UUID — no `@ManyToOne` (avoids circular FK with `ArticleImage`)
- `Article.tags` managed via `@ManyToMany` on `Article` → `ContentTag` through `article_content_tags`
- **N+1 prevention:** `listArticles` and `listPublishedArticles` fetch the article page first, then batch-load associated tags using `contentTagRepo.findAllById(...)` grouped by article — same pattern as `ProductService.toAdminResponse` using `blobRepo.findAllById(blobIds)`
- `Page.searchVector` and `Article.searchVector` are mapped as `@Column(name = "search_vector", insertable = false, updatable = false, columnDefinition = "tsvector")` with Java type `Object` (mapped via `@JdbcTypeCode(SqlTypes.OTHER)`) — the `columnDefinition` ensures schema validation passes against PostgreSQL's `tsvector` type; the field is never read by the application, only referenced via `root.get("searchVector")` in `cb.function(...)`

---

## Section 2: API Surface

### Storefront (public, no authentication)

```
GET /api/v1/pages                                           — list PUBLISHED non-deleted pages
GET /api/v1/pages/{handle}                                  — get PUBLISHED page by handle (404 if DRAFT or deleted)
GET /api/v1/blogs                                           — list all blogs
GET /api/v1/blogs/{blogHandle}                              — get blog by handle (404 if not found)
GET /api/v1/blogs/{blogHandle}/articles                     — list PUBLISHED non-deleted articles in blog (404 if blog not found)
GET /api/v1/blogs/{blogHandle}/articles/{articleHandle}     — get PUBLISHED article by handle (404 if DRAFT, deleted, or blog not found)
```

All storefront list endpoints accept `page` (default 0) and `pageSize` (default 10) query parameters.

**Storefront filter params:**

| Endpoint | Filter params |
|---|---|
| `GET /api/v1/pages` | `q` — full-text search over `search_vector` (title + body) |
| `GET /api/v1/blogs` | `titleContains` — case-insensitive ILIKE on title |
| `GET /api/v1/blogs/{blogHandle}/articles` | `tag` — exact tag name match; `q` — full-text search over `search_vector` (title + excerpt + body) |

### Admin

```
// Pages
GET    /api/v1/admin/pages                                  content:read   — list all non-deleted pages
GET    /api/v1/admin/pages/{id}                             content:read
POST   /api/v1/admin/pages                                  content:write  — create; status defaults to DRAFT; returns 201
PUT    /api/v1/admin/pages/{id}                             content:write  — full replace
PATCH  /api/v1/admin/pages/{id}/status                      content:publish
DELETE /api/v1/admin/pages/{id}                             content:delete — soft delete; returns 204

// Blogs
GET    /api/v1/admin/blogs                                  content:read   — list all blogs
GET    /api/v1/admin/blogs/{id}                             content:read
POST   /api/v1/admin/blogs                                  content:write  — returns 201
PUT    /api/v1/admin/blogs/{id}                             content:write
DELETE /api/v1/admin/blogs/{id}                             content:delete — hard delete, cascades articles; returns 204

// Articles
GET    /api/v1/admin/blogs/{id}/articles                    content:read   — list non-deleted articles
GET    /api/v1/admin/blogs/{id}/articles/{articleId}        content:read
POST   /api/v1/admin/blogs/{id}/articles                    content:write  — create; status defaults to DRAFT; returns 201
PUT    /api/v1/admin/blogs/{id}/articles/{articleId}        content:write  — full replace
PATCH  /api/v1/admin/blogs/{id}/articles/{articleId}/status content:publish
DELETE /api/v1/admin/blogs/{id}/articles/{articleId}        content:delete — soft delete; returns 204

// Article Images
POST   /api/v1/admin/blogs/{id}/articles/{articleId}/images           content:write — attach by blobId; returns 201
PATCH  /api/v1/admin/blogs/{id}/articles/{articleId}/images/positions content:write — reorder; returns 200
DELETE /api/v1/admin/blogs/{id}/articles/{articleId}/images/{imageId} content:delete — returns 204
```

All admin list endpoints accept `page` (default 0) and `pageSize` (default 10) query parameters.

**Admin filter params:**

| Endpoint | Filter params |
|---|---|
| `GET /api/v1/admin/pages` | `status` (`DRAFT`/`PUBLISHED`); `titleContains`; `handleContains`; `q` — full-text search over `search_vector` |
| `GET /api/v1/admin/blogs` | `titleContains`; `handleContains` |
| `GET /api/v1/admin/blogs/{id}/articles` | `status` (`DRAFT`/`PUBLISHED`); `titleContains`; `handleContains`; `authorId` (UUID); `tag` (tag name); `q` — full-text search over `search_vector` |

All filter params are optional. Multiple params are ANDed together. `q` and `titleContains` may be combined.

---

## Section 3: Services and Behaviors

### PageService

| Method | Behavior |
|---|---|
| `create(CreatePageRequest)` → `AdminPageResponse` | Slugify handle from title if not provided (fallback: `"page"`). Check handle unique among non-deleted (`ConflictException` on collision). Status defaults to `DRAFT`. |
| `list(PageFilterRequest, Pageable)` → `PagedResult<AdminPageResponse>` | All non-deleted pages, ordered by `createdAt` desc. Applies `PageSpecification.toSpec(filter)`. |
| `get(UUID id)` → `AdminPageResponse` | Non-deleted or `NotFoundException`. |
| `update(UUID id, UpdatePageRequest)` → `AdminPageResponse` | Full replace of all updatable fields. Handle uniqueness checked excluding self. |
| `changeStatus(UUID id, PageStatusRequest)` → `AdminPageResponse` | Transition to PUBLISHED: sets `publishedAt = Instant.now()`. Transition to DRAFT: clears `publishedAt = null`. |
| `delete(UUID id)` | Sets `deletedAt = Instant.now()`. |
| `getByHandle(String handle)` → `PageResponse` | Storefront: PUBLISHED + non-deleted only. `NotFoundException` otherwise. |
| `listPublished(PageFilterRequest, Pageable)` → `PagedResult<PageResponse>` | Storefront: PUBLISHED + non-deleted, ordered by `publishedAt` desc. Applies `PageSpecification.toSpec(filter)`. |

### ArticleService

**Blog operations:**

| Method | Behavior |
|---|---|
| `createBlog(CreateBlogRequest)` → `AdminBlogResponse` | Handle unique check (`ConflictException`). |
| `listBlogs(BlogFilterRequest, Pageable)` → `PagedResult<AdminBlogResponse>` | All blogs, ordered by `createdAt` desc. Applies `BlogSpecification.toSpec(filter)`. |
| `getBlog(UUID id)` → `AdminBlogResponse` | `NotFoundException` if missing. |
| `updateBlog(UUID id, UpdateBlogRequest)` → `AdminBlogResponse` | Full replace. Handle uniqueness checked excluding self. |
| `deleteBlog(UUID id)` | Hard delete. DB cascade removes articles and their images. |
| `getBlogByHandle(String handle)` → `BlogResponse` | Storefront: `NotFoundException` if not found. |
| `listBlogsPublic(BlogFilterRequest, Pageable)` → `PagedResult<BlogResponse>` | Storefront: all blogs, ordered by `createdAt` desc. Applies `BlogSpecification.toSpec(filter)`. |

**Article operations:**

| Method | Behavior |
|---|---|
| `createArticle(UUID blogId, CreateArticleRequest)` → `AdminArticleResponse` | Verify blog exists. Handle unique within blog among non-deleted (fallback slug: `"article"`). Status defaults to DRAFT. Tags: find-or-create `ContentTag` by name. |
| `listArticles(UUID blogId, ArticleFilterRequest, Pageable)` → `PagedResult<AdminArticleResponse>` | All non-deleted articles for blog, ordered by `createdAt` desc. Batch-load tags. Applies `ArticleSpecification.toSpec(blogId, filter)`. |
| `getArticle(UUID blogId, UUID articleId)` → `AdminArticleResponse` | Article must belong to blog; non-deleted or `NotFoundException`. |
| `updateArticle(UUID blogId, UUID articleId, UpdateArticleRequest)` → `AdminArticleResponse` | Full replace. Handle uniqueness within blog excluding self. Tags replaced (find-or-create). |
| `changeArticleStatus(UUID blogId, UUID articleId, ArticleStatusRequest)` → `AdminArticleResponse` | Transition to PUBLISHED: sets `publishedAt = Instant.now()`. If `authorName` is currently null and `authorId` is set, snapshot `firstName + " " + lastName` from `UserRepository`. Transition to DRAFT: clears `publishedAt = null`. `authorName` is never updated after it is first set. |
| `deleteArticle(UUID blogId, UUID articleId)` | Soft delete (`deletedAt = Instant.now()`). |
| `getArticleByHandle(String blogHandle, String articleHandle)` → `ArticleResponse` | Storefront: verifies blog exists by handle (`NotFoundException` if not), then finds PUBLISHED + non-deleted article by handle within that blog (`NotFoundException` if not found). |
| `listPublishedArticles(String blogHandle, ArticleFilterRequest, Pageable)` → `PagedResult<ArticleResponse>` | Storefront: verifies blog exists by handle (`NotFoundException` if not), then lists PUBLISHED + non-deleted articles ordered by `publishedAt` desc. Batch-load tags. Applies `ArticleSpecification.toSpec(blogId, filter)`. |

**Image operations (same pattern as `ProductImageService`):**

| Method | Behavior |
|---|---|
| `addImage(UUID blogId, UUID articleId, CreateImageRequest)` → `ArticleImageResponse` | Verify article belongs to blog. Attach by `blobId`. Position = count + 1. Auto-sets `featuredImageId` on the article if currently null. |
| `deleteImage(UUID blogId, UUID articleId, UUID imageId)` | Verify article belongs to blog. Hard delete. If deleted image was featured: promote next by lowest position, or set `featuredImageId = null` if none remain. |
| `reorderImages(UUID blogId, UUID articleId, List<ImagePositionItem>)` | Verify article belongs to blog. Update positions via JPA dirty-checking (no explicit `save` calls in loop). |

### Handle Slugification

Same algorithm as `ProductService.slugify`: lowercase → replace non-alphanumeric runs with `-` → trim leading/trailing dashes → fallback to `"page"` (pages) or `"article"` (articles) or `"blog"` (blogs) if result is empty.

---

## Section 4: Filtering

### Filter Request Records

Each list endpoint accepts a filter record (all fields nullable/optional):

```java
// io.k2dv.garden.content.dto
record PageFilterRequest(String status, String titleContains, String handleContains, String q) {}
record BlogFilterRequest(String titleContains, String handleContains) {} // handleContains is admin-only; storefront controller binds only titleContains
record ArticleFilterRequest(String status, String titleContains, String handleContains, UUID authorId, String tag, String q) {}
```

Controllers bind these from `@RequestParam` fields directly (individual params, not a request body).

### Specification Builders

One static factory per entity in `io.k2dv.garden.content.specification`:

**`PageSpecification.toSpec(PageFilterRequest filter)`** returns `Specification<Page>`:
- Always adds: `deletedAt IS NULL`
- `status` → `cb.equal(root.get("status"), PageStatus.valueOf(status))`
- `titleContains` → `cb.like(cb.lower(root.get("title")), "%" + titleContains.toLowerCase() + "%")`
- `handleContains` → `cb.like(cb.lower(root.get("handle")), "%" + handleContains.toLowerCase() + "%")`
- `q` → full-text: `cb.isTrue(cb.function("fts_match", Boolean.class, root.get("searchVector"), cb.literal(q)))` — implemented as a Hibernate `FunctionContributor`-registered function that emits `?1 @@ websearch_to_tsquery('english', ?2)`.

**`BlogSpecification.toSpec(BlogFilterRequest filter)`** returns `Specification<Blog>`:
- `titleContains` → ILIKE on title
- `handleContains` → ILIKE on handle

**`ArticleSpecification.toSpec(UUID blogId, ArticleFilterRequest filter)`** returns `Specification<Article>`:
- Always adds: `blogId = ?` and `deletedAt IS NULL`
- `status` → exact match on `ArticleStatus`
- `titleContains` → ILIKE on title
- `handleContains` → ILIKE on handle
- `authorId` → `cb.equal(root.get("authorId"), authorId)`
- `tag` → `root.join("tags", JoinType.INNER)` then `cb.equal(tagJoin.get("name"), tag)` — uses the `Article.tags` `@ManyToMany` path directly; no manual subquery needed. Spring Data JPA's pagination `COUNT` query uses `COUNT(DISTINCT root.id)` automatically to avoid inflated counts from the JOIN.
- `q` → full-text match on `search_vector` (same pattern as pages)

### Full-Text Search Implementation

`q` is passed to PostgreSQL via `websearch_to_tsquery('english', q)` (supports `+`, `-`, `"phrase"` syntax). The `@@` match is registered as a Hibernate `FunctionContributor` so it can be used inside a `Specification`:

```java
// io.k2dv.garden.config.ContentFunctionContributor implements FunctionContributor
// Registered via META-INF/services/org.hibernate.boot.model.FunctionContributor
@Override
public void contributeFunctions(FunctionContributions contributions) {
    contributions.getFunctionRegistry()
        .registerPattern("fts_match", "?1 @@ websearch_to_tsquery('english', ?2)");
}
```

Call site in any Specification:
```java
Predicate fts = cb.isTrue(
    cb.function("fts_match", Boolean.class, root.get("searchVector"), cb.literal(q))
);
```

`FunctionContributor` is the correct Hibernate 6 SPI for registering custom SQL functions. `HibernatePropertiesCustomizer` and dialect `@PostConstruct` do not have access to `SqmFunctionRegistry` and must not be used for this purpose.

### Null Safety

Every predicate is only added when its corresponding filter field is non-null and non-blank. Specs are combined with `Specification.where(...).and(...)`. If all fields are null the spec is `null`, which `JpaSpecificationExecutor.findAll(null, pageable)` treats as no filter.

---

## Section 5: Pagination

All list endpoints use Spring Data `Pageable` constructed in the controller from `page` and `pageSize` query parameters. Sort field is endpoint-specific:

| Endpoint | Sort |
|---|---|
| Admin page list | `createdAt` desc |
| Storefront page list | `publishedAt` desc |
| Blog list (admin + storefront) | `createdAt` desc |
| Admin article list | `createdAt` desc |
| Storefront article list | `publishedAt` desc |

Pattern:
```java
Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
Page<T> result = repo.findAll(spec, pageable);
return PagedResult.of(result);
```

Default: `page=0`, `pageSize=10`.

---

## Section 6: Testing

### Integration Tests (extend `AbstractIntegrationTest`)

**`PageServiceIT`:**
- `createPage_autoHandle_draftStatus`: title → handle slugified, status = DRAFT
- `createPage_duplicateHandle_throwsConflict`: second page with same title throws `ConflictException`
- `changeStatus_toPublished_setsPublishedAt`: `publishedAt` set on PUBLISHED transition
- `changeStatus_toDraft_clearsPublishedAt`: `publishedAt` cleared on revert to DRAFT
- `softDelete_excludedFromStorefrontList`: deleted page not returned by `listPublished`
- `listPages_filterByStatus_returnsDraftOnly`: `status=DRAFT` filter returns only draft pages
- `listPages_filterByQ_matchesTitleOnInsert`: immediately after `create(...)` (no update), `q` matching a word from the title returns the page — verifies INSERT trigger fires
- `listPages_filterByQ_matchesBody`: `q` matching a word in page body returns the page

**`ArticleServiceIT`:**
- `createBlog_persistsBlog`: blog saved with correct handle
- `createArticle_withTags_findsOrCreatesTags`: two articles sharing a tag reuse the same `ContentTag` row
- `createArticle_sameHandle_differentBlog_succeeds`: same handle allowed in different blogs
- `createArticle_sameHandle_sameBlog_throwsConflict`: same handle in same blog throws `ConflictException`
- `addFirstImage_setsFeaturedImageId`: first image auto-sets `featuredImageId`
- `deleteFeaturedImage_promotesNext`: deleting featured image promotes next by position
- `publishedArticle_visibleOnStorefront`: PUBLISHED article returned by `listPublishedArticles`
- `draftArticle_notVisibleOnStorefront`: DRAFT article not returned by `listPublishedArticles`
- `changeStatus_toPublished_snapshotsAuthorName`: `authorName` set to `firstName + " " + lastName` on first publish
- `listArticles_filterByTag_returnsMatchingArticles`: `tag=java` returns only articles tagged with "java"
- `listArticles_filterByAuthorId_returnsMatchingArticles`: `authorId` filter returns only that author's articles
- `listArticles_filterByQ_matchesTitleOnInsert`: immediately after `createArticle(...)` (no update), `q` matching the title returns the article — verifies INSERT trigger fires
- `listArticles_filterByQ_matchesExcerptAndBody`: `q` matching words across excerpt and body returns the article
- `listPublishedArticles_filterByTag_excludesDraftAndUntagged`: storefront tag filter returns only PUBLISHED articles with that tag

### Controller Slice Tests (`@WebMvcTest` + `@MockitoBean`)

**`AdminPageControllerTest`:** create 201, blank title 400, not found 404, status change 200, delete 204, list with `status` filter 200.

**`AdminArticleControllerTest`:** create article 201, get article 200, delete 204, add image 201, list with `tag` filter 200.

**`StorefrontContentControllerTest`:** published page 200, draft page 404, published article 200, draft article 404, list articles with `tag` filter 200.

---

## Deferred

- Navigation menus (`Menu`, `MenuItem`) — to be designed and implemented in a future iteration.
