# Garden — Shopify-Clone Design Spec

**Date:** 2026-03-21
**Project:** `io.k2dv.garden`
**Stack:** Spring Boot 4.0.4 · Java 26 · PostgreSQL · Flyway · Hibernate 6.5+ · Spring Security (OAuth2) · Lombok

---

## Overview

Garden is a single-tenant e-commerce REST API backend modeled after Shopify's resource graph. It serves a separate React/Next.js frontend. The product is a landscape goods vendor (planters, green roof trays, edging) but the API is designed to be product-agnostic.

The project is delivered in two phases:

- **Phase 1 (soft launch):** Products visible online, customer sign-up, storefront content, admin management
- **Phase 2:** Quote system, cart, orders, payments (Stripe)

---

## Architecture

### Approach

Package-by-feature (domain-driven). Each domain owns its own `controller/`, `service/`, `repository/`, `model/`, and `dto/` sub-packages. Domains communicate through service interfaces only — never by reaching into another domain's repository directly.

### Domain Map

| Package | Phase | Responsibility |
|---|---|---|
| `shared` | 1 | Base entities, response wrappers, exceptions, pagination |
| `auth` | 1 | JWT issuance, login, registration, Google OAuth2 |
| `user` | 1 | User accounts, profiles, addresses |
| `iam` | 1 | Roles, permissions, role-permission and user-role assignments |
| `blob` | 1 | BlobObject entity, StorageService abstraction, S3-compatible upload/delete |
| `product` | 1 | Products, variants, options, collections, tags, images |
| `inventory` | 1 | Locations, inventory items/levels, adjustment ledger |
| `content` | 1 | Pages, blogs, articles, navigation menus |
| `quote` | 2 | Quote requests, line items, admin quote publishing, email dispatch |
| `cart` | 2 | Cart sessions and line items |
| `order` | 2 | Order lifecycle, fulfillment, shipping |
| `payment` | 2 | Stripe integration, payment intents, refunds |

### Open Decision

Whether `Location` (warehouse/storage areas) should live inside `inventory` or be promoted to its own top-level package. To be revisited when Phase 2 fulfillment and shipping are designed.

---

## Section 1: Shared

### BaseEntity

All entities extend `BaseEntity`:

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.TIME) // UUIDv7 — requires Hibernate 6.2+
    private UUID id;

    // DB-managed timestamps — @Generated lets the DB column default (clock_timestamp())
    // drive the value, so JPA does not override it with Instant.now().
    // This is critical: Hibernate's @CreationTimestamp calls Instant.now() at the Java
    // layer, which behaves like now() (transaction start time) and breaks timestamp
    // ordering in @Transactional tests. Using @Generated ensures clock_timestamp() is
    // used even when rows are inserted via JPA.
    @Generated
    @Column(insertable = false, updatable = false)
    private Instant createdAt;

    @Generated
    @Column(insertable = false, updatable = false)
    private Instant updatedAt;
}
```

Flyway migration column definitions:

```sql
created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
```

An `ON UPDATE` trigger updates `updated_at` to `clock_timestamp()` on every row modification.

### Primary Keys

All primary keys are **UUIDv7** — time-ordered, B-tree friendly. Generated via Hibernate `@UuidGenerator(style = UuidGenerator.Style.TIME)` (requires Hibernate 6.2+; Spring Boot 4.x ships Hibernate 6.5+).

### Why `clock_timestamp()` Not `now()`

`now()` in PostgreSQL returns the transaction start time — the same value for every row inserted within a single transaction. `clock_timestamp()` returns the actual wall-clock time at the moment of the call. Because integration tests wrap all DB operations in a single `@Transactional` block that is rolled back, using `now()` would produce identical timestamps for every row in a test, breaking any ordering or "most recent" logic. `clock_timestamp()` preserves meaningful ordering within a transaction.

### Response Envelope

```json
// Success (single resource)
{ "data": { ... } }

// Success (paginated list)
{
  "data": [ ... ],
  "meta": {
    "nextCursor": "01JQ5...",
    "hasMore": true,
    "pageSize": 20
  }
}

// Error
{ "error": "PRODUCT_NOT_FOUND", "message": "No product with handle 'blue-planter'", "status": 404 }
```

A global exception handler maps domain exceptions to error codes and HTTP status codes.

### Pagination

**Cursor-based** (default for all list endpoints): stable under concurrent inserts.

- Request: `GET /api/v1/products?cursor={cursor}&pageSize=20`
- `cursor` is a base64-encoded UUIDv7 of the last item seen
- Response includes `nextCursor` (null when no more results) and `hasMore: boolean`
- Because UUIDv7 is time-ordered, cursor-based pagination is naturally sorted by creation time

**Offset/page** pagination available for admin list views that need explicit page numbers:
- Request: `GET /api/v1/admin/products?page=2&pageSize=20`
- Response `meta` includes `page`, `pageSize`, `total`

---

## Section 2: Blob Storage

### Design

The `blob` package is an infrastructure domain. It owns the `BlobObject` entity, the `StorageService` interface, and the single S3-compatible implementation. No other domain calls a storage service directly — they delegate to `BlobService` and receive a `BlobObject` back.

**`StorageService` interface:**
```java
public interface StorageService {
    String store(String key, String contentType, InputStream data, long size);
    void delete(String key);
    String resolveUrl(String key);
}
```

**`BlobObject` entity:**
```
BlobObject
  ├─ id (UUIDv7)
  ├─ key          (storage path, e.g. "products/01JQ5abc/hero.jpg")
  ├─ filename     (original upload filename)
  ├─ contentType  (MIME type)
  ├─ size         (bytes)
  └─ createdAt
```

No `url` field. Public URL is always computed: `resolveUrl(key)` = `{storage.base-url}/{key}`.

### Environments

| Environment | Backend | Config |
|---|---|---|
| Local dev / test | Garage or MinIO in Docker Compose | `storage.endpoint`, `storage.bucket`, `storage.access-key`, `storage.secret-key`, `storage.base-url` |
| Production | Cloudflare R2 | Same config keys, different values |

One implementation (`S3StorageService`) serves all environments. No code changes between local and production — only environment config.

### Key Generation

Blob keys are generated by the caller (e.g., `product` domain) using the pattern `{domain}/{resourceId}/{uuidv7}-{sanitized-filename}`. This scopes blobs to their owning resource and avoids collisions.

### API Surface

```
POST   /api/v1/admin/blobs          (any authenticated admin — upload returns BlobObject)
DELETE /api/v1/admin/blobs/{id}     (any authenticated admin — only if blob has no referencing entities)
```

Upload is a multipart request returning the created `BlobObject` (id, key, filename, contentType, size, url). The caller then attaches the `blobId` to the relevant entity (e.g., `ProductImage`).

---

## Section 3: Auth

### Concerns

1. **Email/password** registration and login, password reset
2. **Google Sign-In** via Spring `oauth2-client`
3. **JWT issuance** (access + refresh tokens) via HS256
4. Spring `oauth2-resource-server` validates JWTs on all secured requests

### Identity Model

Authentication credentials are stored in a dedicated `Identity` entity, separate from the `User` profile. One user can have multiple identities — one per provider. This replaces the old `googleId` / `passwordHash` fields on `User` and cleanly supports adding more OAuth providers in the future.

```
User
  └─< Identity
        ├─ id (UUIDv7)
        ├─ userId (FK → User)
        ├─ provider: CREDENTIALS | GOOGLE
        ├─ accountId  (provider's user ID; for CREDENTIALS: same as userId)
        ├─ passwordHash (nullable — only for CREDENTIALS identity)
        ├─ accessToken, refreshToken, idToken (nullable — OAuth2 tokens from provider)
        └─ expiresAt (nullable — OAuth2 token expiry)
```

Account linking: a user who registered with email/password can later add a Google identity to the same `User` record (matched by email). Both identities resolve to the same user.

### User Registration

- Public registration creates a `User` + a `CREDENTIALS` `Identity` (bcrypt-hashed password) and assigns the `CUSTOMER` role
- Staff accounts are created only by `OWNER` or `MANAGER` via the `iam` domain — not via public registration
- Email verification: `emailVerifiedAt` is null on `User` until verified via emailed token
- Unverified users have `status: UNVERIFIED` and may have restricted access to certain endpoints

### Google Sign-In

- Spring handles the OAuth2 authorization code flow. The Spring-managed callback URL is `/login/oauth2/code/google`. The API surface lists `GET /api/v1/auth/oauth2/google` as the initiation endpoint that redirects to Google — Spring internally manages the actual callback.
- On first Google login: create `User` + `GOOGLE` `Identity`, assign `CUSTOMER` role.
- On subsequent logins: look up by `Identity.provider = GOOGLE` and `Identity.accountId = googleSubject`.
- If a `User` already exists with the same email (credentials account): link a new `GOOGLE` `Identity` to the existing `User` rather than creating a duplicate.

### Tokens

**Access token:** JWT (HS256), 15-minute TTL.

Payload:
```json
{
  "sub": "01JQ5...",
  "email": "user@example.com",
  "permissions": ["product:read", "content:read"],
  "emailVerifiedAt": "2026-03-21T10:00:00Z",
  "iat": 1234567890,
  "exp": 1234568790
}
```

Permissions are loaded fresh from the DB when the access token is minted (both on login and on each `/auth/refresh` call). This ensures that role/permission changes take effect within one 15-minute TTL window.

**Access token revocation:** There is no token blacklist in Phase 1. A suspended or deleted user's current access token remains valid until it expires (up to 15 minutes). The short TTL is the primary mitigation. A token filter that checks `user.status` on each request would provide immediate revocation but adds a DB lookup per request — this is a Phase 2 consideration.

**Stateful tokens:** A single `Token` entity covers refresh tokens, email verification tokens, and password reset tokens — differentiated by `type`. Token value is stored as a SHA-256 hash (not plain text). Tokens are hard-deleted on use (rotation = delete old, insert new).

```
Token
  ├─ id (UUIDv7)
  ├─ userId (FK → User)
  ├─ type: REFRESH_TOKEN | EMAIL_VERIFICATION | PASSWORD_RESET
  ├─ tokenHash (SHA-256 of the opaque token string)
  └─ expiresAt
```

TTLs: `REFRESH_TOKEN` 30 days · `EMAIL_VERIFICATION` 24 hours · `PASSWORD_RESET` 24 hours.

### Custom Security Annotations

Adopted from the reference implementation for readability:

| Annotation | Equivalent | Usage |
|---|---|---|
| `@Authenticated` | `@PreAuthorize("isAuthenticated()")` | Any endpoint requiring a logged-in user |
| `@CurrentUser` | Argument resolver | Injects the `User` entity directly into controller method params |
| `@HasPermission("product:write")` | `@PreAuthorize("hasAuthority('product:write')")` | Fine-grained permission checks |

### Owner Seeding

A `SuperUserCommand` (Spring `ApplicationRunner`) seeds the initial `OWNER` account on first startup if no `OWNER` user exists. Credentials are read from environment config (`SUPERUSER_EMAIL`, `SUPERUSER_PASSWORD`).

### API Surface

```
POST /api/v1/auth/register
POST /api/v1/auth/login
POST /api/v1/auth/refresh
POST /api/v1/auth/logout
POST /api/v1/auth/resend-verification
GET  /api/v1/auth/verify-email?token={token}
POST /api/v1/auth/request-password-reset
POST /api/v1/auth/confirm-password-reset/{token}
POST /api/v1/auth/update-password              (authenticated — change own password)
GET  /api/v1/auth/oauth2/google                (redirects to Google; Spring manages callback at /login/oauth2/code/google)
```

---

## Section 4: User & IAM

### User Model

```
User
  ├─ id (UUIDv7), email
  ├─ firstName, lastName, phone
  ├─ status: ACTIVE | SUSPENDED | UNVERIFIED
  ├─ emailVerifiedAt (nullable)
  ├─< Identity (see Section 3 — auth credentials and OAuth2 provider accounts)
  └─< Address
        ├─ firstName, lastName, company
        ├─ address1, address2, city, province, zip, country
        └─ isDefault (one global default per user — setting a new default clears the previous one)
```

`User` no longer holds `passwordHash` or `googleId` — those live in `Identity`. `User` is a pure profile record.

### RBAC Model

```
User └─< UserRole >─ Role └─< RolePermission >─ Permission
```

- `Permission`: `name` (e.g. `product:write`), `resource`, `action`
- `Role`: `name`, `description`, set of `Permission`s
- `UserRole`: links a `User` to a `Role`

### Predefined Roles

| Role | Type | Description |
|---|---|---|
| `CUSTOMER` | Storefront | Browse, manage own profile and addresses |
| `STAFF` | Admin | Manage products, inventory, content |
| `MANAGER` | Admin | Staff + manage orders, discounts, staff accounts |
| `OWNER` | Admin | Full access including settings, roles, permissions |

### OWNER Wildcard

`OWNER` bypasses all permission checks. Implementation: when minting the JWT for an `OWNER`, the `permissions` array is populated with all known permission strings (expanded from DB). This means `hasAuthority('product:write')` in `@PreAuthorize` resolves to true without any custom wildcard logic — no special `GrantedAuthority` or `AccessDecisionVoter` needed. The expansion happens in the token-minting service when it detects the `OWNER` role.

### Permission Matrix

| Resource | Actions |
|---|---|
| `product` | `read`, `write`, `publish`, `delete` |
| `inventory` | `read`, `write` |
| `content` | `read`, `write`, `publish`, `delete` |
| `user` | `read`, `write` |
| `staff` | `manage` |
| `iam` | `manage` |

Permission enforcement via Spring Security `@PreAuthorize("hasAuthority('product:write')")` at the service layer.

Because `/auth/refresh` re-queries permissions from DB when minting the new access token, role/permission changes propagate within one 15-minute window.

### API Surface

```
// Own account
GET    /api/v1/account
PUT    /api/v1/account
GET    /api/v1/account/addresses
POST   /api/v1/account/addresses
PUT    /api/v1/account/addresses/{id}
DELETE /api/v1/account/addresses/{id}

// Admin — users
GET    /api/v1/admin/users                         (user:read)
GET    /api/v1/admin/users/{id}                    (user:read)
PUT    /api/v1/admin/users/{id}                    (user:write)
PUT    /api/v1/admin/users/{id}/suspend            (staff:manage)
POST   /api/v1/admin/users/{id}/roles              (iam:manage)
DELETE /api/v1/admin/users/{id}/roles/{roleId}     (iam:manage)

// Admin — IAM
GET    /api/v1/admin/iam/roles                     (iam:manage)
POST   /api/v1/admin/iam/roles                     (iam:manage)
PUT    /api/v1/admin/iam/roles/{id}                (iam:manage)
DELETE /api/v1/admin/iam/roles/{id}                (iam:manage)
GET    /api/v1/admin/iam/permissions               (iam:manage)
POST   /api/v1/admin/iam/roles/{id}/permissions    (iam:manage)
DELETE /api/v1/admin/iam/roles/{id}/permissions/{permissionId}  (iam:manage)
```

---

## Section 5: Product Catalog

### Model

```
Product
  ├─ title, description (HTML), handle (slug), vendor, productType
  ├─ status: DRAFT | ACTIVE | ARCHIVED
  ├─ tags (many-to-many)
  ├─ featuredImageId (nullable FK to ProductImage)
  ├─ deletedAt (soft delete)
  ├─< ProductImage
  │     ├─ blobId (FK → BlobObject)
  │     ├─ altText, position (ordering)
  │     └─ productId
  ├─< ProductOption (e.g. "Size", "Color")
  │     └─< ProductOptionValue ("Small", "Large")
  └─< ProductVariant
        ├─ title (auto-generated on save: concatenation of selected option values, e.g. "Small / Green")
        ├─ sku, barcode, price, compareAtPrice
        ├─ weight, weightUnit
        ├─ deletedAt (soft delete)
        └─ InventoryItem (1:1)
```

Variant title generation: on save, the service concatenates the variant's selected `ProductOptionValue` labels in option-declaration order, joined by ` / `. Recomputed whenever variant options change.

### Collections

**CustomCollection** — manually curated:
```
CustomCollection
  ├─ title, handle, description, image
  ├─ status: DRAFT | PUBLISHED
  └─< CollectionProduct (join, with position for ordering)
```

**SmartCollection** — rule-based, evaluated on every product save AND whenever the SmartCollection's rules are updated. When rules change, the service performs a **synchronous** full re-evaluation of all active (non-deleted) products against the new ruleset within the same request transaction. For Phase 1 catalog sizes this is acceptable; if the catalog grows large, this can be made asynchronous in a future iteration:
```
SmartCollection
  ├─ title, handle, description, image
  ├─ status: DRAFT | PUBLISHED
  ├─ disjunctive (boolean — true = ANY rule matches, false = ALL rules must match)
  └─< CollectionRule
        ├─ field: TAG | PRODUCT_TYPE | VENDOR | PRICE | TITLE
        ├─ relation: EQUALS | NOT_EQUALS | CONTAINS | NOT_CONTAINS | GREATER_THAN | LESS_THAN
        └─ value (string — for PRICE comparisons, a plain decimal string with period separator and no currency symbol, e.g. "49.99"; invalid formats are rejected with a validation error at rule creation time)
```

Both `CustomCollection` and `SmartCollection` are returned from the unified `/collections` endpoints. Responses include a `type` discriminator field (`CUSTOM` | `SMART`) so consumers can distinguish them.

### API Surface

```
// Storefront (public)
GET /api/v1/products
GET /api/v1/products/{handle}
GET /api/v1/collections
GET /api/v1/collections/{handle}
GET /api/v1/collections/{handle}/products

// Admin
GET    /api/v1/admin/products                  (product:read)
GET    /api/v1/admin/products/{id}             (product:read)
POST   /api/v1/admin/products                  (product:write)
PUT    /api/v1/admin/products/{id}             (product:write)
DELETE /api/v1/admin/products/{id}             (product:delete)

GET    /api/v1/admin/products/{id}/variants                                          (product:read)
POST   /api/v1/admin/products/{id}/variants                                         (product:write)
PUT    /api/v1/admin/products/{id}/variants/{variantId}                             (product:write)
DELETE /api/v1/admin/products/{id}/variants/{variantId}                             (product:delete)

GET    /api/v1/admin/products/{id}/options                                           (product:read)
POST   /api/v1/admin/products/{id}/options                                          (product:write)
PUT    /api/v1/admin/products/{id}/options/{optionId}                               (product:write)
DELETE /api/v1/admin/products/{id}/options/{optionId}                               (product:delete)
POST   /api/v1/admin/products/{id}/options/{optionId}/values                        (product:write)
PUT    /api/v1/admin/products/{id}/options/{optionId}/values/{valueId}              (product:write)
DELETE /api/v1/admin/products/{id}/options/{optionId}/values/{valueId}              (product:delete)

POST   /api/v1/admin/products/{id}/images           (product:write — multipart upload)
DELETE /api/v1/admin/products/{id}/images/{imageId} (product:delete)
PUT    /api/v1/admin/products/{id}/images/reorder   (product:write)

GET    /api/v1/admin/collections                            (product:read)
GET    /api/v1/admin/collections/{id}                       (product:read)
POST   /api/v1/admin/collections                            (product:write)
PUT    /api/v1/admin/collections/{id}                       (product:write)
DELETE /api/v1/admin/collections/{id}                       (product:delete)

// Custom collection product membership
POST   /api/v1/admin/collections/{id}/products              (product:write)
DELETE /api/v1/admin/collections/{id}/products/{productId}  (product:delete)
PUT    /api/v1/admin/collections/{id}/products/reorder      (product:write)
```

---

## Section 6: Inventory

### Model

```
InventoryItem       (1:1 with ProductVariant)
Location            (warehouse, showroom, outdoor storage, etc.)
  ├─ name, address (optional), isActive
InventoryLevel      (item + location → cached quantity on hand)
  └─ quantityOnHand  (running total — updated atomically with each InventoryTransaction insert)
InventoryTransaction (append-only ledger — never updated or deleted)
  ├─ item, location, delta (positive or negative)
  ├─ reason: RECEIVED | SOLD | RETURNED | DAMAGED | WRITE_OFF | COUNT_ADJUSTMENT
  ├─ note
  ├─ performedBy (User)
  └─ createdAt
```

**Ledger approach:** Every stock change creates an `InventoryTransaction`. `InventoryLevel.quantityOnHand` is a cached running total updated atomically alongside the transaction insert. The ledger is the audit source of truth; the cached level is the read source of truth.

**`InventoryTransaction` and `BaseEntity`:** `InventoryTransaction` does NOT extend `BaseEntity`. It is a standalone entity with only `id` (UUIDv7) and `createdAt` (using the same `@Generated` + `clock_timestamp()` convention). There is no `updatedAt` because the ledger is append-only.

Phase 2 adds a `quantityReserved` column to `InventoryLevel` via a new Flyway migration. Available = `onHand - reserved`.

### API Surface

```
GET    /api/v1/admin/inventory/levels                 (inventory:read)
GET    /api/v1/admin/inventory/transactions           (inventory:read)
POST   /api/v1/admin/inventory/adjustments            (inventory:write)
GET    /api/v1/admin/inventory/locations              (inventory:read)
POST   /api/v1/admin/inventory/locations              (inventory:write)
PUT    /api/v1/admin/inventory/locations/{id}         (inventory:write)  — accepts isActive to deactivate/reactivate a location
```

---

## Section 7: Storefront Content

### Pages

```
Page
  ├─ title, handle, body (HTML)
  ├─ status: DRAFT | PUBLISHED
  ├─ metaTitle, metaDescription
  ├─ publishedAt, deletedAt
```

### Blog & Articles

```
Blog
  ├─ title, handle
  └─< Article
        ├─ title, handle, body (HTML), excerpt
        ├─ authorId (nullable FK to User — nullable so articles survive user deletion)
        ├─ authorName (denormalized snapshot of author display name at time of publish)
        ├─ tags
        ├─ status: DRAFT | PUBLISHED
        ├─ featuredImageId (nullable FK to ArticleImage — mirrors Product.featuredImageId pattern)
        ├─ metaTitle, metaDescription
        ├─ publishedAt, deletedAt
        └─< ArticleImage
              ├─ blobId (FK → BlobObject)
              ├─ altText, position
              └─ articleId
```

`ArticleImage` mirrors `ProductImage` for consistency — structured entity with `featuredImageId` on the parent (not `isFeatured` on the image). `authorId` is a nullable FK: if the author user is deleted, the article is preserved with `authorId = null` but `authorName` retains the display name snapshot.

### Navigation Menus

```
Menu
  ├─ title, handle (e.g. "main-menu", "footer")
  └─< MenuItem (ordered, self-referential for one level of nesting)
        ├─ title, url
        ├─ type: URL | PAGE | COLLECTION | PRODUCT | BLOG | ARTICLE
        ├─ resourceId (nullable — UUID of target resource)
        ├─ position
        └─ parentId (nullable)
```

`MenuItem` nesting is limited to one level (parent items cannot themselves have a `parentId`). This constraint is enforced at the service layer: on create/update, the service rejects a `parentId` that points to a `MenuItem` which already has a `parentId`.

### API Surface

```
// Storefront (public)
GET /api/v1/pages
GET /api/v1/pages/{handle}
GET /api/v1/blogs
GET /api/v1/blogs/{blogHandle}
GET /api/v1/blogs/{blogHandle}/articles
GET /api/v1/blogs/{blogHandle}/articles/{articleHandle}
GET /api/v1/menus/{handle}

// Admin
GET    /api/v1/admin/pages                                   (content:read)
GET    /api/v1/admin/pages/{id}                              (content:read)
POST   /api/v1/admin/pages                                   (content:write)
PUT    /api/v1/admin/pages/{id}                              (content:write)
DELETE /api/v1/admin/pages/{id}                              (content:delete)

GET    /api/v1/admin/blogs                                   (content:read)
GET    /api/v1/admin/blogs/{id}                              (content:read)
POST   /api/v1/admin/blogs                                   (content:write)
PUT    /api/v1/admin/blogs/{id}                              (content:write)
DELETE /api/v1/admin/blogs/{id}                              (content:delete)

GET    /api/v1/admin/blogs/{id}/articles                     (content:read)
GET    /api/v1/admin/blogs/{id}/articles/{articleId}         (content:read)
POST   /api/v1/admin/blogs/{id}/articles                                        (content:write)
PUT    /api/v1/admin/blogs/{id}/articles/{articleId}                            (content:write)
DELETE /api/v1/admin/blogs/{id}/articles/{articleId}                            (content:delete)
POST   /api/v1/admin/blogs/{id}/articles/{articleId}/images                     (content:write — multipart upload)
DELETE /api/v1/admin/blogs/{id}/articles/{articleId}/images/{imageId}           (content:delete)
PUT    /api/v1/admin/blogs/{id}/articles/{articleId}/images/reorder             (content:write)

GET    /api/v1/admin/menus                                   (content:read)
GET    /api/v1/admin/menus/{id}                              (content:read)
POST   /api/v1/admin/menus                                   (content:write)
PUT    /api/v1/admin/menus/{id}                              (content:write)
DELETE /api/v1/admin/menus/{id}                              (content:delete)
POST   /api/v1/admin/menus/{id}/items                        (content:write)
PUT    /api/v1/admin/menus/{id}/items/{itemId}               (content:write)
DELETE /api/v1/admin/menus/{id}/items/{itemId}               (content:delete)
PUT    /api/v1/admin/menus/{id}/items/reorder                (content:write)
```

---

## Section 8: Cross-Cutting Concerns

### API Conventions

- All endpoints: `/api/v1/`
- Public storefront: `/api/v1/{resource}`
- Admin: `/api/v1/admin/{resource}` — secured by permissions
- Public lookups use `handle` (URL slug); admin/internal use UUID
- HTTP verbs: `GET` read, `POST` create, `PUT` full update, `DELETE` remove
- `PATCH` is intentionally excluded. `PUT` replaces the full resource representation.

### Database Conventions

- **Flyway migrations:** `V{n}__{description}.sql`. One file per schema change. `spring.jpa.hibernate.ddl-auto=validate`.
- **Column naming:** snake_case in DB (e.g., `created_at`, `product_type`). Java fields use camelCase. Spring/Hibernate naming strategy maps automatically.
- **Timestamps:** All `created_at`/`updated_at` column defaults use `clock_timestamp()`. An `ON UPDATE` trigger keeps `updated_at` current. `BaseEntity` uses `@Generated` on both fields so JPA reads the DB-assigned value rather than overriding it with `Instant.now()`.
- **Primary keys:** UUIDv7 everywhere. Hibernate `@UuidGenerator(style = UuidGenerator.Style.TIME)` (Hibernate 6.2+; Spring Boot 4.x ships 6.5+).
- **Soft deletes:** Products, variants, pages, articles use `deleted_at` (nullable timestamp). Preserves referential integrity for Phase 2 order history. All repository queries — both public storefront and standard admin list endpoints — automatically filter `WHERE deleted_at IS NULL`. There are no Phase 1 endpoints for viewing or restoring soft-deleted records.

### Object Storage

All file uploads are managed by the `blob` domain. Other domains (`product`, `content`) reference blobs by FK — they never call a storage service directly.

**StorageService abstraction:**
```java
public interface StorageService {
    BlobObject store(String filename, String contentType, InputStream data, long size);
    void delete(String key);
    String resolveUrl(String key); // derives public URL from key + configured base URL
}
```

One implementation — `S3StorageService` — covers all environments. S3-compatible APIs are spoken by both Garage/MinIO (local) and Cloudflare R2 (production). The endpoint URL, bucket name, access key, and secret are injected from environment-specific config.

**Local development:** A Garage (or MinIO) container in Docker Compose, configured with the same S3 SDK settings. No code changes between environments — only config.

**Production:** Cloudflare R2. Public URL is derived at read time from `key` + the configured `storage.base-url` (e.g. `https://assets.yourdomain.com`). The URL is never stored in the DB — if the CDN domain changes, updating one config property is sufficient.

**`BlobObject` entity (in `blob` package):**
```
BlobObject
  ├─ id (UUIDv7)
  ├─ key        (storage path, e.g. "products/01JQ5abc/hero.jpg")
  ├─ filename   (original upload name)
  ├─ contentType (MIME type)
  ├─ size       (bytes)
  └─ createdAt
```

No `url` field is stored. The URL is always computed: `resolveUrl(key)` = `{storage.base-url}/{key}`.

### Testing Strategy

- **Integration tests** use Testcontainers to spin up a real PostgreSQL instance — no DB mocking.
- All integration tests annotated `@Transactional` + `@Rollback`. Each test runs inside a transaction that is rolled back on completion, leaving no state behind.
- Because all DB operations in a test share one transaction, `now()` returns the same timestamp for every row. `clock_timestamp()` column defaults ensure rows get distinct, ordered timestamps even within a single transaction.
- **Caveat:** Any service method annotated `@Transactional(propagation = REQUIRES_NEW)` runs in its own transaction and is NOT rolled back by the test's outer rollback. Use `REQUIRES_NEW` sparingly; document it when used, and handle test cleanup explicitly for those paths.
- Unit tests for pure business logic (e.g., SmartCollection rule evaluation, permission resolution) use no DB.

---

## Phase 2 Roadmap

### Quote System

Replaces a traditional cart for this vendor's workflow:

1. Customer builds a quote request (line items, notes, delivery address)
2. Admin reviews and publishes a formal quote with pricing
3. System emails the quote to the customer
4. Customer accepts → quote converts to an order

```
QuoteRequest
  ├─ customer (User), status: DRAFT | SUBMITTED | QUOTED | ACCEPTED | DECLINED | EXPIRED
  ├─ notes, deliveryAddress
  └─< QuoteLineItem
        ├─ productVariant, quantity, requestedPrice (nullable)
        └─ quotedPrice (nullable — set by admin)
```

### Cart, Orders, Payments

Standard Shopify-style flow added after quote system is validated:
- `Cart` → `Order` → `Payment` (Stripe PaymentIntent) → `Fulfillment`
- A `quantityReserved` column is added to `InventoryLevel` via Flyway migration. Updated when an order or quote is placed; released on cancellation. Available = `onHand - reserved`.

---

## Summary

| Concern | Decision |
|---|---|
| Architecture | Package-by-feature (domain-driven) |
| Tenancy | Single-tenant |
| API style | REST, `/api/v1/`, JSON |
| Auth | Self-contained Spring Security JWT (HS256) + Google OAuth2 |
| Identity model | `User` + `Identity` (one per provider); supports account linking |
| Stateful tokens | Single `Token` entity (`REFRESH_TOKEN`, `EMAIL_VERIFICATION`, `PASSWORD_RESET`); SHA-256 hash stored; hard-delete on use |
| Authorization | RBAC — `user → roles → permissions`, JWT carries flat permission list |
| OWNER bypass | Expand all known permissions into JWT at mint time |
| Owner seeding | `SuperUserCommand` seeds initial OWNER on first startup from env config |
| Primary keys | UUIDv7 via Hibernate `@UuidGenerator(style = TIME)` (Hibernate 6.2+) |
| Timestamps | `clock_timestamp()` in Flyway defaults; `@Generated` in BaseEntity (not `@CreationTimestamp`) |
| Migrations | Flyway, validate-only DDL |
| Inventory tracking | Ledger-based (append-only transactions + cached level) |
| Soft deletes | `deleted_at` on products, variants, pages, articles |
| Images | `ProductImage` / `ArticleImage` reference `BlobObject` by FK; URL derived at read time |
| Object storage | `blob` domain owns `StorageService` interface + S3-compatible impl; Garage/MinIO locally, R2 in prod |
| Pagination | Cursor-based (UUIDv7 cursor); offset available for admin views |
| Testing | Testcontainers + `@Transactional` + `@Rollback`; `clock_timestamp()` ensures ordering within transaction |
| Open decision | `Location` boundary — revisit in Phase 2 |
