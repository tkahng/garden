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

Handle uniqueness: service checks uniqueness within the same `blog_id` among non-deleted articles. Throws `ConflictException` on collision. Two articles in different blogs may share the same handle.

#### `article_images`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` default |
| `article_id` | UUID NOT NULL FK→articles | |
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

---

## Section 3: Services and Behaviors

### PageService

| Method | Behavior |
|---|---|
| `create(CreatePageRequest)` → `AdminPageResponse` | Slugify handle from title if not provided (fallback: `"page"`). Check handle unique among non-deleted (`ConflictException` on collision). Status defaults to `DRAFT`. |
| `list(Pageable)` → `PagedResult<AdminPageResponse>` | All non-deleted pages, ordered by `createdAt` desc. |
| `get(UUID id)` → `AdminPageResponse` | Non-deleted or `NotFoundException`. |
| `update(UUID id, UpdatePageRequest)` → `AdminPageResponse` | Full replace of all updatable fields. Handle uniqueness checked excluding self. |
| `changeStatus(UUID id, PageStatusRequest)` → `AdminPageResponse` | Transition to PUBLISHED: sets `publishedAt = Instant.now()`. Transition to DRAFT: clears `publishedAt = null`. |
| `delete(UUID id)` | Sets `deletedAt = Instant.now()`. |
| `getByHandle(String handle)` → `PageResponse` | Storefront: PUBLISHED + non-deleted only. `NotFoundException` otherwise. |
| `listPublished(Pageable)` → `PagedResult<PageResponse>` | Storefront: PUBLISHED + non-deleted, ordered by `publishedAt` desc. |

### ArticleService

**Blog operations:**

| Method | Behavior |
|---|---|
| `createBlog(CreateBlogRequest)` → `AdminBlogResponse` | Handle unique check (`ConflictException`). |
| `listBlogs(Pageable)` → `PagedResult<AdminBlogResponse>` | All blogs, ordered by `createdAt` desc. |
| `getBlog(UUID id)` → `AdminBlogResponse` | `NotFoundException` if missing. |
| `updateBlog(UUID id, UpdateBlogRequest)` → `AdminBlogResponse` | Full replace. Handle uniqueness checked excluding self. |
| `deleteBlog(UUID id)` | Hard delete. DB cascade removes articles and their images. |
| `getBlogByHandle(String handle)` → `BlogResponse` | Storefront: `NotFoundException` if not found. |
| `listBlogsPublic(Pageable)` → `PagedResult<BlogResponse>` | Storefront: all blogs, ordered by `createdAt` desc. |

**Article operations:**

| Method | Behavior |
|---|---|
| `createArticle(UUID blogId, CreateArticleRequest)` → `AdminArticleResponse` | Verify blog exists. Handle unique within blog among non-deleted (fallback slug: `"article"`). Status defaults to DRAFT. Tags: find-or-create `ContentTag` by name. |
| `listArticles(UUID blogId, Pageable)` → `PagedResult<AdminArticleResponse>` | All non-deleted articles for blog, ordered by `createdAt` desc. Batch-load tags to avoid N+1. |
| `getArticle(UUID blogId, UUID articleId)` → `AdminArticleResponse` | Article must belong to blog; non-deleted or `NotFoundException`. |
| `updateArticle(UUID blogId, UUID articleId, UpdateArticleRequest)` → `AdminArticleResponse` | Full replace. Handle uniqueness within blog excluding self. Tags replaced (find-or-create). |
| `changeArticleStatus(UUID blogId, UUID articleId, ArticleStatusRequest)` → `AdminArticleResponse` | Transition to PUBLISHED: sets `publishedAt = Instant.now()`. If `authorName` is currently null and `authorId` is set, snapshot `firstName + " " + lastName` from `UserRepository`. Transition to DRAFT: clears `publishedAt = null`. `authorName` is never updated after it is first set. |
| `deleteArticle(UUID blogId, UUID articleId)` | Soft delete (`deletedAt = Instant.now()`). |
| `getArticleByHandle(String blogHandle, String articleHandle)` → `ArticleResponse` | Storefront: verifies blog exists by handle (`NotFoundException` if not), then finds PUBLISHED + non-deleted article by handle within that blog (`NotFoundException` if not found). |
| `listPublishedArticles(String blogHandle, Pageable)` → `PagedResult<ArticleResponse>` | Storefront: verifies blog exists by handle (`NotFoundException` if not), then lists PUBLISHED + non-deleted articles ordered by `publishedAt` desc. Batch-load tags. |

**Image operations (same pattern as `ProductImageService`):**

| Method | Behavior |
|---|---|
| `addImage(UUID blogId, UUID articleId, CreateImageRequest)` → `ArticleImageResponse` | Verify article belongs to blog. Attach by `blobId`. Position = count + 1. Auto-sets `featuredImageId` on the article if currently null. |
| `deleteImage(UUID blogId, UUID articleId, UUID imageId)` | Verify article belongs to blog. Hard delete. If deleted image was featured: promote next by lowest position, or set `featuredImageId = null` if none remain. |
| `reorderImages(UUID blogId, UUID articleId, List<ImagePositionItem>)` | Verify article belongs to blog. Update positions via JPA dirty-checking (no explicit `save` calls in loop). |

### Handle Slugification

Same algorithm as `ProductService.slugify`: lowercase → replace non-alphanumeric runs with `-` → trim leading/trailing dashes → fallback to `"page"` (pages) or `"article"` (articles) or `"blog"` (blogs) if result is empty.

---

## Section 4: Pagination

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

## Section 5: Testing

### Integration Tests (extend `AbstractIntegrationTest`)

**`PageServiceIT`:**
- `createPage_autoHandle_draftStatus`: title → handle slugified, status = DRAFT
- `createPage_duplicateHandle_throwsConflict`: second page with same title throws `ConflictException`
- `changeStatus_toPublished_setsPublishedAt`: `publishedAt` set on PUBLISHED transition
- `changeStatus_toDraft_clearsPublishedAt`: `publishedAt` cleared on revert to DRAFT
- `softDelete_excludedFromStorefrontList`: deleted page not returned by `listPublished`

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

### Controller Slice Tests (`@WebMvcTest` + `@MockitoBean`)

**`AdminPageControllerTest`:** create 201, blank title 400, not found 404, status change 200, delete 204.

**`AdminArticleControllerTest`:** create article 201, get article 200, delete 204, add image 201.

**`StorefrontContentControllerTest`:** published page 200, draft page 404, published article 200, draft article 404.

---

## Deferred

- Navigation menus (`Menu`, `MenuItem`) — to be designed and implemented in a future iteration.
