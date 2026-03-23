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
    ProductTag.java           # JPA entity (extends BaseEntity)
    InventoryItem.java        # JPA entity (extends BaseEntity)
  repository/
    ProductRepository.java
    ProductImageRepository.java
    ProductOptionRepository.java
    ProductOptionValueRepository.java
    ProductVariantRepository.java
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
    AdminProductController.java    # /api/v1/admin/products/**
    StorefrontProductController.java  # /api/v1/products/**
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

### ProductTag

| Field | Type   | Notes                           |
|-------|--------|---------------------------------|
| name  | String | NOT NULL UNIQUE                 |

Products and tags are linked via a join table `product_tags (product_id, tag_id)`.

### InventoryItem

| Field            | Type    | Notes                                              |
|------------------|---------|----------------------------------------------------|
| variantId        | UUID    | FK → product_variants.id, UNIQUE, column: `variant_id` |
| requiresShipping | boolean | NOT NULL, default true                             |

---

## Flyway Migration

**`V9__create_products.sql`** — all product catalog tables + permission seed:

```sql
CREATE TABLE products (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title            TEXT        NOT NULL,
    description      TEXT,
    handle           TEXT        NOT NULL UNIQUE,
    vendor           TEXT,
    product_type     TEXT,
    status           TEXT        NOT NULL DEFAULT 'DRAFT',
    featured_image_id UUID,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
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
    id        UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    option_id UUID        NOT NULL REFERENCES product_options(id),
    label     TEXT        NOT NULL,
    position  INT         NOT NULL,
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

CREATE TABLE product_tags (
    id   UUID NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name TEXT NOT NULL UNIQUE,
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

-- Seed product permissions (static UUIDs following 00000000-0000-7000-8000-000000000XXX pattern)
INSERT INTO permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000027', 'product:create',     'product', 'create',     clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000028', 'product:update',     'product', 'update',     clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000029', 'product:delete',     'product', 'delete',     clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000030', 'product:read-admin', 'product', 'read-admin', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- Assign to OWNER and MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('OWNER', 'MANAGER')
  AND p.name IN ('product:create', 'product:update', 'product:delete', 'product:read-admin')
ON CONFLICT DO NOTHING;
```

---

## API Surface

### Admin Endpoints

All admin endpoints require the matching `product:*` permission enforced via `@HasPermission`.

```
POST   /api/v1/admin/products                         product:create
GET    /api/v1/admin/products                         product:read-admin  (paginated, filter by status/tag)
GET    /api/v1/admin/products/{id}                    product:read-admin
PATCH  /api/v1/admin/products/{id}                    product:update
DELETE /api/v1/admin/products/{id}                    product:delete      (soft delete)

POST   /api/v1/admin/products/{id}/variants           product:create
PATCH  /api/v1/admin/products/{id}/variants/{vId}     product:update
DELETE /api/v1/admin/products/{id}/variants/{vId}     product:delete      (soft delete)

POST   /api/v1/admin/products/{id}/images             product:create
DELETE /api/v1/admin/products/{id}/images/{imgId}     product:delete
PATCH  /api/v1/admin/products/{id}/images/reorder     product:update

POST   /api/v1/admin/products/{id}/options            product:create
DELETE /api/v1/admin/products/{id}/options/{optId}    product:delete

GET    /api/v1/admin/products/{id}/inventory          product:read-admin
PATCH  /api/v1/admin/inventory/{itemId}               product:update
```

### Storefront Endpoints

Public — no authentication required.

```
GET    /api/v1/products                  list ACTIVE products (cursor-paginated)
GET    /api/v1/products/{handle}         get by handle (ACTIVE only, 404 if DRAFT/ARCHIVED/deleted)
```

Storefront responses omit internal fields and only surface ACTIVE products and non-soft-deleted variants.

---

## Key Behaviors

### Handle generation
- If `handle` is not provided on create, auto-generate by slugifying `title`: lowercase, replace spaces and non-alphanumeric characters with `-`, collapse consecutive dashes
- `handle` must be unique — throw `ValidationException("HANDLE_CONFLICT", "A product with this handle already exists")` on conflict

### Variant title generation
- Auto-generated by joining option value labels with ` / ` (e.g. `"Red / Large"`)
- If the product has no options, title defaults to `"Default Title"`

### InventoryItem auto-creation
- When a `ProductVariant` is saved, `VariantService` automatically creates an `InventoryItem` with `requiresShipping = true`
- The InventoryItem lifecycle is owned by the variant; deleting a variant soft-deletes the variant (InventoryItem row remains for audit)

### Price validation
- `price` is required on variant create
- If `compareAtPrice` is provided, it must be ≥ `price` — throw `ValidationException("INVALID_COMPARE_PRICE", "Compare-at price must be greater than or equal to price")`

### Soft deletes
- `DELETE /products/{id}` sets `deletedAt = now()` on the product
- `DELETE /variants/{variantId}` sets `deletedAt = now()` on the variant only
- All queries exclude soft-deleted records by default
- Storefront never surfaces soft-deleted products or variants

### Image management
- On `POST /products/{id}/images`: new image gets `position = max(existing) + 1`. If `featuredImageId` is null (no prior images), automatically set `product.featuredImageId = newImage.id`
- On `DELETE /products/{id}/images/{imgId}`: if the deleted image was the featured image, auto-promote the image with the next-lowest position as the new `featuredImageId`. If no images remain, set `featuredImageId = null`
- `PATCH /images/reorder` accepts `[{id, position}]` and bulk-updates positions only; does not change `featuredImageId`
- `PATCH /products/{id}` can explicitly override `featuredImageId` to any `ProductImage` id belonging to that product

### Tags
- `ProductTag` rows are shared across products (unique by `name`)
- Tags are created on demand when referenced in a product create/update request
- Linking is via the `product_product_tags` join table

---

## Response DTOs

```java
// Storefront
public record ProductSummaryResponse(UUID id, String title, String handle, String vendor, String url) {}
public record ProductDetailResponse(UUID id, String title, String description, String handle,
    String vendor, String productType, List<ProductVariantResponse> variants,
    List<ProductImageResponse> images, List<String> tags) {}

// Admin (includes status, deletedAt)
public record AdminProductResponse(UUID id, String title, String description, String handle,
    String vendor, String productType, String status, UUID featuredImageId,
    List<AdminVariantResponse> variants, List<ProductImageResponse> images,
    List<String> tags, Instant createdAt, Instant updatedAt, Instant deletedAt) {}

public record ProductVariantResponse(UUID id, String title, String sku, BigDecimal price,
    BigDecimal compareAtPrice) {}
public record AdminVariantResponse(UUID id, String title, String sku, String barcode,
    BigDecimal price, BigDecimal compareAtPrice, BigDecimal weight, String weightUnit,
    Instant deletedAt) {}
public record ProductImageResponse(UUID id, String url, String altText, int position) {}
```

---

## Testing

### Slice Tests (`@WebMvcTest`)

**`AdminProductControllerTest`:**
- `POST /api/v1/admin/products` with valid body → 201
- `POST /api/v1/admin/products` with missing title → 400
- `GET /api/v1/admin/products/{id}` for non-existent id → 404
- `DELETE /api/v1/admin/products/{id}` → 204

**`StorefrontProductControllerTest`:**
- `GET /api/v1/products` → 200 with cursor-paginated results
- `GET /api/v1/products/{handle}` for ACTIVE product → 200
- `GET /api/v1/products/{handle}` for DRAFT product → 404

All slice tests: `@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})`, `@MockitoBean` for services.

### Integration Tests (`@SpringBootTest`)

**`ProductServiceIT`** (extends `AbstractIntegrationTest`):
- Create product → verify persisted with DRAFT status and auto-generated handle
- Create product with duplicate handle → verify `ValidationException`
- Add variant → verify `InventoryItem` auto-created
- Soft delete product → verify excluded from list queries
- Storefront list → verify only ACTIVE products returned

**`ProductImageServiceIT`** (extends `AbstractIntegrationTest`):
- Add first image → verify `product.featuredImageId` set automatically
- Add second image → verify `featuredImageId` unchanged
- Delete non-featured image → verify `featuredImageId` unchanged
- Delete featured image → verify `featuredImageId` promoted to next image by position
- Delete last image → verify `featuredImageId` set to null

### Permission count
After V9 migration, total permissions = 20 (16 from blobs + 4 product permissions). Tests that assert permission count must be updated.

---

## Dependencies

No new dependencies required. `BigDecimal` is standard Java. All other infrastructure (JPA, Spring MVC, PostgreSQL Testcontainer) is already present.
