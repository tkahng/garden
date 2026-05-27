# Content Spec — Blog, Pages, Search, Recommendations, Newsletter

**Status:** Current  
**Last updated:** 2026-05-26  
**Packages:** `io.k2dv.garden.content`, `io.k2dv.garden.search`, `io.k2dv.garden.recommendation`, `io.k2dv.garden.newsletter`  
**DB schema:** `content`, `marketing`

---

## Blog & Articles

### Blog
`content.blogs`

| Field | Constraint |
|---|---|
| title | not null |
| handle | unique, not null |

---

### Article
`content.articles`

| Field | Constraint | Notes |
|---|---|---|
| blogId | not null, FK | |
| title | not null | |
| handle | not null | unique within blog |
| body | text | |
| excerpt | text, nullable | |
| authorId | UUID, nullable | |
| authorName | nullable | |
| status | ArticleStatus, not null | |
| featuredImageId | UUID, nullable | |
| metaTitle, metaDescription | nullable | |
| publishedAt | Instant, nullable | |
| deletedAt | Instant, nullable | soft delete |
| tags | ManyToMany → ContentTag | |

**ArticleStatus enum:** `DRAFT` · `PUBLISHED` · `ARCHIVED`

---

### SitePage
`content.pages`

| Field | Constraint | Notes |
|---|---|---|
| title | not null | |
| handle | unique, not null | |
| body | text | |
| status | PageStatus, not null | |
| metaTitle, metaDescription | nullable | |
| publishedAt | Instant, nullable | |
| deletedAt | soft delete | |

**PageStatus enum:** `DRAFT` · `PUBLISHED` · `ARCHIVED`

---

### ContentTag
`content.content_tags` — shared tag entity linked to articles via join table.

---

## API — Admin Blog/Articles (`/api/v1/admin/blogs`)

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `content:read` | List blogs |
| POST | `/` | `content:write` | Create blog |
| PUT | `/{id}` | `content:write` | Update blog |
| DELETE | `/{id}` | `content:delete` | Delete blog |
| GET | `/{blogId}/articles` | `content:read` | List articles |
| POST | `/{blogId}/articles` | `content:write` | Create article |
| GET | `/{blogId}/articles/{id}` | `content:read` | Get article |
| PUT | `/{blogId}/articles/{id}` | `content:write` | Update article |
| DELETE | `/{blogId}/articles/{id}` | `content:delete` | Soft-delete |
| POST | `/{blogId}/articles/{id}/publish` | `content:write` | Set PUBLISHED |
| POST | `/{blogId}/articles/{id}/images` | `content:write` | Upload article image |

---

## API — Admin Pages (`/api/v1/admin/pages`)

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `content:read` | List pages |
| POST | `/` | `content:write` | Create page |
| GET | `/{id}` | `content:read` | Get page |
| PUT | `/{id}` | `content:write` | Update page |
| DELETE | `/{id}` | `content:delete` | Soft-delete |
| POST | `/{id}/publish` | `content:write` | Set PUBLISHED |

---

## API — Storefront Content (no auth)

| Method | Path | Description |
|---|---|---|
| GET | `/api/v1/blogs` | List blogs |
| GET | `/api/v1/blogs/{handle}/articles` | List published articles |
| GET | `/api/v1/blogs/{handle}/articles/{articleHandle}` | Get published article |
| GET | `/api/v1/pages/{handle}` | Get published page |

---

## Search

### SearchController (`/api/v1/search`, no auth)

| Method | Path | Description |
|---|---|---|
| GET | `/?q=&types=&page=&limit=` | Full-text search |

**Parameters:**
- `q` — required, not blank
- `types` — comma-separated: `products`, `collections`, `articles`, `pages`; defaults to all
- `limit` — clamped to max 50

**SearchService logic:**
- Products: title/description/handle match; active only
- Collections: title/description match; published only
- Articles: title/body match; published only
- Pages: title/body match; published only

Returns `SearchResponse { products: PagedResult, collections: PagedResult, articles: PagedResult, pages: PagedResult }`.

---

## Recommendations

### StorefrontRecommendationController (`/api/v1/recommendations`, no auth)

| Method | Path | Description |
|---|---|---|
| GET | `/?handle=&limit=` | Related products for a product handle |

**RecommendationService logic:**
1. Load source product by handle
2. Find products with tag overlap (excluding self)
3. Order by overlap count (most tags in common first)
4. Limit to min(requested, 12)
5. Resolve variant prices and featured image URLs
6. Returns `List<ProductSummaryResponse>`

---

## Newsletter

### NewsletterSubscriber
`marketing.newsletter_subscribers`

| Field | Constraint | Notes |
|---|---|---|
| email | unique, not null | |
| source | nullable | where subscription originated |
| subscribedAt | Instant, auto | |
| unsubscribedAt | Instant, nullable | null = active subscriber |

### NewsletterController (`/api/v1/newsletter`, no auth)

| Method | Path | Description |
|---|---|---|
| POST | `/subscribe` | Subscribe; idempotent re-subscribe if previously unsubscribed |

**Response:** `{ alreadySubscribed: boolean }` — HTTP 201 on new, 200 on existing.

**NewsletterService:** `subscribe(email, source)` — if unsubscribedAt is set, clears it; if already active, returns `alreadySubscribed = true` without update.
