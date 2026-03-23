# Content Domain Design Spec

**Date:** 2026-03-23
**Project:** `io.k2dv.garden`
**Domain:** `content`

---

## Goal

Build a content management domain covering static pages, blogs, and articles. Navigation menus are deferred to a future iteration.

---

## Architecture

Package-by-feature under `io.k2dv.garden.content`. Three services own distinct sub-domains: `PageService` owns pages, `ArticleService` owns blogs + articles + article images + content tags. All repositories extend `JpaSpecificationExecutor<T>` for `Specification`-based filtering. All list endpoints use offset pagination via Spring Data `Pageable` and return `PagedResult<T>`.

**Tech Stack:** Spring Boot 4.x, Spring Data JPA (`JpaSpecificationExecutor`), Hibernate 6, Flyway, Lombok, PostgreSQL, Testcontainers (IT), MockMvc (slice tests).

---

## Section 1: Data Model

### V10 Migration (DDL only)

All content permissions (`content:read`, `content:write`, `content:publish`, `content:delete`) are already seeded in V6. V10 contains no `INSERT` statements.

### Tables

#### `pages`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `title` | TEXT NOT NULL | |
| `handle` | TEXT NOT NULL UNIQUE | URL slug, globally unique among non-deleted |
| `body` | TEXT | HTML body |
| `status` | TEXT NOT NULL DEFAULT 'DRAFT' | `DRAFT` or `PUBLISHED` |
| `meta_title` | TEXT | |
| `meta_description` | TEXT | |
| `published_at` | TIMESTAMPTZ | Set when status → PUBLISHED; cleared when → DRAFT |
| `deleted_at` | TIMESTAMPTZ | Soft delete |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |

#### `blogs`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `title` | TEXT NOT NULL | |
| `handle` | TEXT NOT NULL UNIQUE | Globally unique |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |

No soft delete on blogs — deleting a blog hard-deletes and cascades to its articles.

#### `articles`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `blog_id` | UUID NOT NULL FK→blogs | |
| `title` | TEXT NOT NULL | |
| `handle` | TEXT NOT NULL | Unique within blog among non-deleted |
| `body` | TEXT | HTML body |
| `excerpt` | TEXT | |
| `author_id` | UUID FK→users | Nullable — preserved when author is deleted |
| `author_name` | TEXT | Denormalized snapshot of author display name at publish time |
| `status` | TEXT NOT NULL DEFAULT 'DRAFT' | `DRAFT` or `PUBLISHED` |
| `featured_image_id` | UUID | Plain UUID column, no FK (avoids circular dependency with `article_images`) |
| `meta_title` | TEXT | |
| `meta_description` | TEXT | |
| `published_at` | TIMESTAMPTZ | Set when status → PUBLISHED; cleared when → DRAFT |
| `deleted_at` | TIMESTAMPTZ | Soft delete |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |

Handle uniqueness is enforced at the service layer: unique within a blog among non-deleted articles.

#### `article_images`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `article_id` | UUID NOT NULL FK→articles | |
| `blob_id` | UUID NOT NULL FK→blob_objects | |
| `alt_text` | TEXT | |
| `position` | INT NOT NULL | Display order |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |

#### `content_tags`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `name` | TEXT NOT NULL UNIQUE | |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |

#### `article_content_tags` (join table)

| Column | Type | Notes |
|---|---|---|
| `article_id` | UUID NOT NULL FK→articles | |
| `content_tag_id` | UUID NOT NULL FK→content_tags | |
| PRIMARY KEY | `(article_id, content_tag_id)` | |

No `id`, `created_at`, or `updated_at` on the join table.

### JPA Entity Notes

- All entities extend `BaseEntity` (`id`, `createdAt`, `updatedAt` via `@Generated`)
- `Article.featuredImageId` is a plain `@Column` UUID — no `@ManyToOne` (avoids circular FK with `ArticleImage`)
- `Article.tags` managed via `@ManyToMany` on `Article` → `ContentTag` through `article_content_tags`
- `ArticleService` uses `VariantService`-style batch fetching where N+1 risk exists

---

## Section 2: API Surface

### Storefront (public, no authentication)

```
GET /api/v1/pages                                           — list PUBLISHED non-deleted pages
GET /api/v1/pages/{handle}                                  — get PUBLISHED page by handle (404 if DRAFT or deleted)
GET /api/v1/blogs                                           — list all blogs
GET /api/v1/blogs/{blogHandle}                              — get blog by handle
GET /api/v1/blogs/{blogHandle}/articles                     — list PUBLISHED non-deleted articles in blog
GET /api/v1/blogs/{blogHandle}/articles/{articleHandle}     — get PUBLISHED article by handle (404 if DRAFT or deleted)
```

All storefront list endpoints accept `page` (default 0) and `pageSize` (default 10) query parameters.

### Admin

```
// Pages
GET    /api/v1/admin/pages                                  content:read   — list all non-deleted pages
GET    /api/v1/admin/pages/{id}                             content:read
POST   /api/v1/admin/pages                                  content:write  — create; status defaults to DRAFT
PUT    /api/v1/admin/pages/{id}                             content:write  — full replace
PATCH  /api/v1/admin/pages/{id}/status                      content:publish
DELETE /api/v1/admin/pages/{id}                             content:delete — soft delete

// Blogs
GET    /api/v1/admin/blogs                                  content:read   — list all blogs
GET    /api/v1/admin/blogs/{id}                             content:read
POST   /api/v1/admin/blogs                                  content:write
PUT    /api/v1/admin/blogs/{id}                             content:write
DELETE /api/v1/admin/blogs/{id}                             content:delete — hard delete, cascades articles

// Articles
GET    /api/v1/admin/blogs/{id}/articles                    content:read   — list non-deleted articles
GET    /api/v1/admin/blogs/{id}/articles/{articleId}        content:read
POST   /api/v1/admin/blogs/{id}/articles                    content:write  — create; status defaults to DRAFT
PUT    /api/v1/admin/blogs/{id}/articles/{articleId}        content:write  — full replace
PATCH  /api/v1/admin/blogs/{id}/articles/{articleId}/status content:publish
DELETE /api/v1/admin/blogs/{id}/articles/{articleId}        content:delete — soft delete

// Article Images
POST   /api/v1/admin/blogs/{id}/articles/{articleId}/images          content:write — attach by blobId
PATCH  /api/v1/admin/blogs/{id}/articles/{articleId}/images/positions content:write — reorder
DELETE /api/v1/admin/blogs/{id}/articles/{articleId}/images/{imageId} content:delete
```

All admin list endpoints accept `page` (default 0) and `pageSize` (default 10) query parameters.

---

## Section 3: Services and Behaviors

### PageService

| Method | Behavior |
|---|---|
| `create(CreatePageRequest)` → `AdminPageResponse` | Slugify handle from title if not provided. Check handle unique among non-deleted (`ConflictException` on collision). Status defaults to `DRAFT`. |
| `list(Pageable)` → `PagedResult<AdminPageResponse>` | All non-deleted pages, `Specification` filtering, ordered by `createdAt` desc. |
| `get(UUID id)` → `AdminPageResponse` | Non-deleted or `NotFoundException`. |
| `update(UUID id, UpdatePageRequest)` → `AdminPageResponse` | Full replace of all updatable fields. Handle uniqueness checked excluding self. |
| `changeStatus(UUID id, PageStatusRequest)` → `AdminPageResponse` | Transition to PUBLISHED sets `publishedAt = Instant.now()`. Transition to DRAFT clears `publishedAt`. |
| `delete(UUID id)` | Sets `deletedAt = Instant.now()`. |
| `getByHandle(String handle)` → `PageResponse` | Storefront: PUBLISHED + non-deleted only. `NotFoundException` otherwise. |
| `listPublished(Pageable)` → `PagedResult<PageResponse>` | Storefront: PUBLISHED + non-deleted, ordered by `publishedAt` desc. |

### ArticleService

**Blog operations:**

| Method | Behavior |
|---|---|
| `createBlog(CreateBlogRequest)` → `AdminBlogResponse` | Handle unique check. |
| `listBlogs(Pageable)` → `PagedResult<AdminBlogResponse>` | All blogs. |
| `getBlog(UUID id)` → `AdminBlogResponse` | `NotFoundException` if missing. |
| `updateBlog(UUID id, UpdateBlogRequest)` → `AdminBlogResponse` | Full replace. Handle uniqueness checked excluding self. |
| `deleteBlog(UUID id)` | Hard delete. Cascades to articles via DB cascade. |
| `getBlogByHandle(String handle)` → `BlogResponse` | Storefront. |

**Article operations:**

| Method | Behavior |
|---|---|
| `createArticle(UUID blogId, CreateArticleRequest)` → `AdminArticleResponse` | Handle unique within blog among non-deleted. Status defaults to DRAFT. Tags: find-or-create `ContentTag` by name. |
| `listArticles(UUID blogId, Pageable)` → `PagedResult<AdminArticleResponse>` | All non-deleted articles for blog. |
| `getArticle(UUID blogId, UUID articleId)` → `AdminArticleResponse` | Must belong to blog; non-deleted or 404. |
| `updateArticle(UUID blogId, UUID articleId, UpdateArticleRequest)` → `AdminArticleResponse` | Full replace. Handle uniqueness within blog excluding self. Tags replaced (find-or-create). |
| `changeArticleStatus(UUID blogId, UUID articleId, ArticleStatusRequest)` → `AdminArticleResponse` | Sets/clears `publishedAt`. Sets `authorName` snapshot from user display name on first publish if `authorId` provided. |
| `deleteArticle(UUID blogId, UUID articleId)` | Soft delete. |
| `getArticleByHandle(String blogHandle, String articleHandle)` → `ArticleResponse` | Storefront: PUBLISHED + non-deleted only. |
| `listPublishedArticles(String blogHandle, Pageable)` → `PagedResult<ArticleResponse>` | Storefront: PUBLISHED + non-deleted, ordered by `publishedAt` desc. |

**Image operations (same pattern as ProductImageService):**

| Method | Behavior |
|---|---|
| `addImage(UUID articleId, CreateImageRequest)` → `ArticleImageResponse` | Attach by `blobId`. Position = count + 1. Auto-sets `featuredImageId` if article has none. |
| `deleteImage(UUID articleId, UUID imageId)` | Hard delete. If deleted image was featured: promote next by lowest position, or set `featuredImageId = null` if none remain. |
| `reorderImages(UUID articleId, List<ImagePositionItem>)` | Update positions via JPA dirty-checking (no explicit `save` calls). |

### Handle Slugification

Same algorithm as `ProductService.slugify`: lowercase → replace non-alphanumeric runs with `-` → trim leading/trailing dashes → fallback to `"page"` or `"article"` if result is empty.

---

## Section 4: Pagination

All list endpoints use Spring Data `Pageable` passed from the controller:

```java
Pageable pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
Page<T> result = repo.findAll(spec, pageable);
return PagedResult.of(result);
```

Default: `page=0`, `pageSize=10`. Controllers parse these from query parameters.

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
- `addFirstImage_setsFeaturedImageId`: first image auto-sets `featuredImageId`
- `deleteFeaturedImage_promotesNext`: deleting featured image promotes next by position
- `publishedArticle_visibleOnStorefront`: PUBLISHED article returned by `listPublishedArticles`
- `draftArticle_notVisibleOnStorefront`: DRAFT article not returned by `listPublishedArticles`

### Controller Slice Tests (`@WebMvcTest` + `@MockitoBean`)

**`AdminPageControllerTest`:** create 201, blank title 400, not found 404, status change 200, delete 204.

**`AdminArticleControllerTest`:** create article 201, get article 200, delete 204, add image 201.

**`StorefrontContentControllerTest`:** published page 200, draft page 404, published article 200, draft article 404.

---

## Deferred

- Navigation menus (`Menu`, `MenuItem`) — to be designed and implemented in a future iteration.
