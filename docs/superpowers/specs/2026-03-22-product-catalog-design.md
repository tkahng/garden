# Product Catalog Design Spec

## Overview

The `product` package owns all product catalog concerns: products, variants, options, images, tags, and inventory stubs. It exposes a public storefront API (read-only, ACTIVE products) and a protected admin API (full CRUD). Collections are a separate domain and are out of scope for this spec. Payments, cart, and orders are Phase 2.

---

## Package Structure

```
io.k2dv.garden.product/
  model/
    Product.java              # JPA entity (extends BaseEntity)
    ProductStatus.java        # enum: DRAFT | ACTIVE | ARCHIVED
    ProductImage.java         # JPA entity (extends BaseEntity)
    ProductOption.java        # JPA entity (extends BaseEntity)
    ProductOptionValue.java   # JPA entity (extends BaseEntity)
    ProductVariant.java       # JPA entity (extends BaseEntity)
    VariantOptionValue.java   # JPA entity (extends BaseEntity) — variant-to-option-value link
    ProductTag.java           # JPA entity (extends BaseEntity)
    InventoryItem.java        # JPA entity (extends BaseEntity)
  repository/
    ProductRepository.java
    ProductImageRepository.java
    ProductOptionRepository.java
    ProductOptionValueRepository.java
    ProductVariantRepository.java
    VariantOptionValueRepository.java
    ProductTagRepository.java
    InventoryItemRepository.java
  service/
    ProductService.java
    VariantService.java
    OptionService.java
    ProductImageService.java
  dto/
    # request/response records per resource
  controller/
    AdminProductController.java          # /api/v1/admin/products/**
    AdminInventoryController.java        # /api/v1/admin/inventory/**
    StorefrontProductController.java     # /api/v1/products/**
```

---

## Data Model

### Product

Extends `BaseEntity` (id UUIDv7, createdAt, updatedAt).

| Field           | Type          | Notes                                                    |
|-----------------|---------------|----------------------------------------------------------|
| title           | String        | NOT NULL                                                 |
| description     | String        | nullable, TEXT                                           |
| handle          | String        | NOT NULL UNIQUE, slugified from title if not provided    |
| vendor          | String        | nullable                                                 |
| productType     | String        | nullable, column: `product_type`                         |
| status          | ProductStatus | NOT NULL, default DRAFT                                  |
| featuredImageId | UUID          | nullable, column: `featured_image_id` (no JPA FK)        |
| deletedAt       | Instant       | nullable, column: `deleted_at` — soft delete marker      |

`featuredImageId` is stored as a plain UUID column, not a JPA `@ManyToOne`, to avoid circular dependency with `ProductImage`.

### ProductImage

| Field     | Type   | Notes                                         |
|-----------|--------|-----------------------------------------------|
| productId | UUID   | FK → products.id, column: `product_id`        |
| blobId    | UUID   | FK → blob_objects.id, column: `blob_id`       |
| altText   | String | nullable, column: `alt_text`                  |
| position  | int    | NOT NULL, 1-based ordering                    |

`blob_objects.id` FK has no `ON DELETE CASCADE` — the blob must outlive its product image. Callers must delete the product image record before deleting the blob object.

### ProductOption

| Field     | Type   | Notes                                  |
|-----------|--------|----------------------------------------|
| productId | UUID   | FK → products.id                       |
| name      | String | NOT NULL (e.g. "Color", "Size")        |
| position  | int    | NOT NULL, 1-based                      |

### ProductOptionValue

| Field    | Type   | Notes                                 |
|----------|--------|---------------------------------------|
| optionId | UUID   | FK → product_options.id               |
| label    | String | NOT NULL (e.g. "Red", "Large")        |
| position | int    | NOT NULL, 1-based                     |

Product options and option values are write-once — there are no update endpoints for either. Variant titles are not retroactively recomputed if an option or its values are deleted.

### ProductVariant

| Field          | Type       | Notes                                                        |
|----------------|------------|--------------------------------------------------------------|
| productId      | UUID       | FK → products.id                                             |
| title          | String     | NOT NULL, auto-generated from option values                  |
| sku            | String     | nullable, UNIQUE                                             |
| barcode        | String     | nullable                                                     |
| price          | BigDecimal | NOT NULL, NUMERIC(19,4)                                      |
| compareAtPrice | BigDecimal | nullable, NUMERIC(19,4), column: `compare_at_price`          |
| weight         | BigDecimal | nullable, NUMERIC(10,4)                                      |
| weightUnit     | String     | nullable, column: `weight_unit` (e.g. "kg", "lb")            |
| deletedAt      | Instant    | nullable, column: `deleted_at` — soft delete marker          |

### VariantOptionValue

Join table entity linking a variant to its selected option values.

| Field         | Type | Notes                                    |
|---------------|------|------------------------------------------|
| variantId     | UUID | FK → product_variants.id                 |
| optionValueId | UUID | FK → product_option_values.id            |

PK is `(variant_id, option_value_id)`. One row per option dimension per variant (e.g., a variant with Color=Red and Size=Large has two rows).

### ProductTag

| Field | Type   | Notes            |
|-------|--------|------------------|
| name  | String | NOT NULL UNIQUE  |

Products and tags are linked via a join table `product_product_tags (product_id, tag_id)`. This name avoids collision with the `product_tags` entity table.

### InventoryItem

| Field            | Type    | Notes                                                  |
|------------------|---------|--------------------------------------------------------|
| variantId        | UUID    | FK → product_variants.id, UNIQUE, column: `variant_id` |
| requiresShipping | boolean | NOT NULL, default true                                 |

`quantity` is out of scope for V9. The InventoryItem is a stub to track shipping requirements; quantity tracking is Phase 2.

---

## Flyway Migration

**`V9__create_products.sql`** — table DDL only. No permission seeding is needed: V6 already seeds all required permissions (`product:read`, `product:write`, `product:delete`, `product:publish`, `inventory:read`, `inventory:write`) and assigns them to the appropriate roles.

```sql
CREATE TABLE products (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title             TEXT        NOT NULL,
    description       TEXT,
    handle            TEXT        NOT NULL UNIQUE,
    vendor            TEXT,
    product_type      TEXT,
    status            TEXT        NOT NULL DEFAULT 'DRAFT',
    featured_image_id UUID,
    deleted_at        TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON products
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_images (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id UUID        NOT NULL REFERENCES products(id),
    blob_id    UUID        NOT NULL REFERENCES blob_objects(id),
    alt_text   TEXT,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON product_images
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_options (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id UUID        NOT NULL REFERENCES products(id),
    name       TEXT        NOT NULL,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON product_options
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_option_values (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    option_id  UUID        NOT NULL REFERENCES product_options(id),
    label      TEXT        NOT NULL,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON product_option_values
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_variants (
    id               UUID           NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    product_id       UUID           NOT NULL REFERENCES products(id),
    title            TEXT           NOT NULL,
    sku              TEXT           UNIQUE,
    barcode          TEXT,
    price            NUMERIC(19,4)  NOT NULL,
    compare_at_price NUMERIC(19,4),
    weight           NUMERIC(10,4),
    weight_unit      TEXT,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ    NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON product_variants
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE variant_option_values (
    variant_id      UUID NOT NULL REFERENCES product_variants(id),
    option_value_id UUID NOT NULL REFERENCES product_option_values(id),
    PRIMARY KEY (variant_id, option_value_id)
);

CREATE TABLE product_tags (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON product_tags
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE product_product_tags (
    product_id UUID NOT NULL REFERENCES products(id),
    tag_id     UUID NOT NULL REFERENCES product_tags(id),
    PRIMARY KEY (product_id, tag_id)
);

CREATE TABLE inventory_items (
    id                UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    variant_id        UUID        NOT NULL UNIQUE REFERENCES product_variants(id),
    requires_shipping BOOLEAN     NOT NULL DEFAULT true,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON inventory_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
```

All child FK constraints default to `RESTRICT` (no cascade). Since all deletes in this domain are soft (products, variants), hard deletes are not expected in normal operation. The FK constraint acts as an intentional safeguard against accidental hard deletes.

---

## Permissions

V6 already seeds and role-assigns all permissions used by this domain:

| Permission        | Used for                                          | Roles                  |
|-------------------|---------------------------------------------------|------------------------|
| `product:read`    | admin GET endpoints, storefront endpoints         | CUSTOMER+, all admins  |
| `product:write`   | create and field-update endpoints                 | STAFF, MANAGER, OWNER  |
| `product:publish` | status change (`PATCH /products/{id}` with status)| STAFF, MANAGER, OWNER  |
| `product:delete`  | soft delete endpoints                             | MANAGER, OWNER         |
| `inventory:read`  | `GET /admin/products/{id}/inventory`              | STAFF, MANAGER, OWNER  |
| `inventory:write` | `PATCH /admin/inventory/{itemId}`                 | STAFF, MANAGER, OWNER  |

V9 adds no new permissions. Total permissions remain **16** (14 from V6 + 2 from V8/blob).

---

## API Surface

### Admin Endpoints

```
POST   /api/v1/admin/products                          product:write
GET    /api/v1/admin/products                          product:read   (paginated, filter by status/tag)
GET    /api/v1/admin/products/{id}                     product:read
PATCH  /api/v1/admin/products/{id}                     product:write  (field updates only)
PATCH  /api/v1/admin/products/{id}/status              product:publish (status transitions only)
DELETE /api/v1/admin/products/{id}                     product:delete (soft delete)

POST   /api/v1/admin/products/{id}/variants            product:write
PATCH  /api/v1/admin/products/{id}/variants/{vId}      product:write
DELETE /api/v1/admin/products/{id}/variants/{vId}      product:delete (soft delete)

POST   /api/v1/admin/products/{id}/images              product:write
DELETE /api/v1/admin/products/{id}/images/{imgId}      product:delete
PATCH  /api/v1/admin/products/{id}/images/positions    product:write  (bulk update positions)

POST   /api/v1/admin/products/{id}/options             product:write
DELETE /api/v1/admin/products/{id}/options/{optId}     product:delete

GET    /api/v1/admin/products/{id}/inventory           inventory:read
PATCH  /api/v1/admin/inventory/{itemId}                inventory:write
```

**Notes:**
- Status changes are a separate endpoint (`PATCH /status`) requiring `product:publish`, keeping it distinct from general field updates (`product:write`).
- Single-resource fetches for variants, images, and options are not exposed as separate endpoints. The full product response (`GET /admin/products/{id}`) embeds variants, images, and options — this is the canonical access path.
- The image positions endpoint uses the literal path segment `/positions` (not `/{imgId}`) and must be registered before the `DELETE /{imgId}` route in the controller to avoid Spring MVC routing ambiguity.
- Inventory GET is nested under a product because items are fetched in bulk per product. Inventory PATCH is top-level because it targets a specific item by its own ID. These are handled by `AdminProductController` and `AdminInventoryController` respectively.

### Storefront Endpoints

Public — no authentication required.

```
GET    /api/v1/products                  list ACTIVE products (cursor-paginated)
GET    /api/v1/products/{handle}         get by handle (ACTIVE only, 404 if DRAFT/ARCHIVED/deleted)
```

Pagination: sorted ascending by `id`. Cursor is the last seen `id`. Default page size 20. Only forward pagination (cursor-after) is supported. Storefront responses omit internal fields and only surface ACTIVE products and non-soft-deleted variants.

---

## Key Behaviors

### Handle generation
- If `handle` is not provided on create, auto-generate by slugifying `title`: normalize to lowercase, replace spaces and non-alphanumeric characters with `-`, collapse consecutive dashes, trim leading/trailing dashes
- Example: `"My Product! (2024)"` → `"my-product-2024"`
- `handle` must be unique — throw `ValidationException("HANDLE_CONFLICT", "A product with this handle already exists")` on both create and update (PATCH)
- `handle` is mutable via `PATCH /products/{id}`; the same uniqueness check applies

### Variant title generation
- Auto-generated by joining option value labels with ` / ` (e.g. `"Red / Large"`)
- If the product has no options, title defaults to `"Default Title"`
- Option values are write-once; no update endpoint exists for `ProductOptionValue`. Variant titles are not retroactively recomputed.

### InventoryItem auto-creation
- When a `ProductVariant` is saved, `VariantService` automatically creates an `InventoryItem` with `requiresShipping = true`
- The InventoryItem row is never soft-deleted; it remains for audit when its variant is soft-deleted

### Price validation
- `price` is required on variant create
- If `compareAtPrice` is provided, it must be strictly greater than `price` — throw `ValidationException("INVALID_COMPARE_PRICE", "Compare-at price must be greater than price")`

### Soft deletes
- `DELETE /products/{id}` sets `deletedAt = now()` on the product
- `DELETE /variants/{variantId}` sets `deletedAt = now()` on the variant only
- All queries exclude soft-deleted records by default
- Storefront never surfaces soft-deleted products or variants
- Requests targeting a soft-deleted product or variant (PATCH, DELETE) return 404

### Image management
- On `POST /products/{id}/images`: new image gets `position = max(existing) + 1`. If `featuredImageId` is null, automatically set `product.featuredImageId = newImage.id`
- On `DELETE /products/{id}/images/{imgId}`: if the deleted image was the featured image, auto-promote the image with the next-lowest position as the new `featuredImageId`. If no images remain, set `featuredImageId = null`
- `PATCH /products/{id}/images/positions` accepts `[{id, position}]` and bulk-updates positions only; does not change `featuredImageId`
- `PATCH /products/{id}` can explicitly override `featuredImageId` to any `ProductImage` id belonging to that product

### Tags
- `ProductTag` rows are shared across products (unique by `name`)
- Tags are created on demand when referenced in a product create/update request
- Linking is via the `product_product_tags` join table

---

## Response DTOs

```java
// Storefront
public record ProductSummaryResponse(UUID id, String title, String handle, String vendor) {}
public record ProductDetailResponse(UUID id, String title, String description, String handle,
    String vendor, String productType, List<ProductVariantResponse> variants,
    List<ProductImageResponse> images, List<String> tags) {}

// Admin (includes status, deletedAt, featuredImageId)
public record AdminProductResponse(UUID id, String title, String description, String handle,
    String vendor, String productType, String status, UUID featuredImageId,
    List<AdminVariantResponse> variants, List<ProductImageResponse> images,
    List<String> tags, Instant createdAt, Instant updatedAt, Instant deletedAt) {}

public record ProductVariantResponse(UUID id, String title, String sku, BigDecimal price,
    BigDecimal compareAtPrice, List<OptionValueLabel> optionValues) {}
public record AdminVariantResponse(UUID id, String title, String sku, String barcode,
    BigDecimal price, BigDecimal compareAtPrice, BigDecimal weight, String weightUnit,
    List<OptionValueLabel> optionValues, Instant deletedAt) {}
public record OptionValueLabel(String optionName, String valueLabel) {}

// ProductImageResponse.url is the blob's public URL resolved by ProductImageService
// via a dependency on BlobService at response time (not stored in the database)
public record ProductImageResponse(UUID id, String url, String altText, int position) {}
```

---

## Testing

### Slice Tests (`@WebMvcTest`)

All slice tests: `@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})`, `@MockitoBean` for services.

**`AdminProductControllerTest`:**
- `POST /api/v1/admin/products` with valid body → 201
- `POST /api/v1/admin/products` with missing title → 400
- `GET /api/v1/admin/products/{id}` for non-existent id → 404
- `PATCH /api/v1/admin/products/{id}/status` → 200
- `DELETE /api/v1/admin/products/{id}` → 204
- `PATCH /api/v1/admin/products/{id}/images/positions` → 200 (verifies routing works before `/{imgId}`)

**`StorefrontProductControllerTest`:**
- `GET /api/v1/products` → 200 with cursor-paginated results
- `GET /api/v1/products/{handle}` for ACTIVE product → 200
- `GET /api/v1/products/{handle}` for DRAFT product → 404

### Integration Tests (`@SpringBootTest`)

Extend `AbstractIntegrationTest`. No additional Testcontainers required.

**`ProductServiceIT`:**
- Create product → verify persisted with DRAFT status and auto-generated handle
- Create product with duplicate handle → verify `ValidationException`
- Add variant with option values → verify `VariantOptionValue` rows created and title auto-generated
- Add variant → verify `InventoryItem` auto-created with `requiresShipping = true`
- Soft delete product → verify excluded from list queries
- Storefront list → verify only ACTIVE products returned

**`ProductImageServiceIT`:**
- Add first image → verify `product.featuredImageId` set automatically
- Add second image → verify `featuredImageId` unchanged
- Delete non-featured image → verify `featuredImageId` unchanged
- Delete featured image → verify `featuredImageId` promoted to next image by position
- Delete last image → verify `featuredImageId` set to null

### Permission count
V9 adds no new permissions. Total remains **16** after migration. Existing permission count assertions do not need updating.
