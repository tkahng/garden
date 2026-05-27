# Catalog Spec — Products, Collections, Reviews

**Status:** Current  
**Last updated:** 2026-05-26  
**Packages:** `io.k2dv.garden.product`, `io.k2dv.garden.collection`, `io.k2dv.garden.review`

---

## Products

### Product
`catalog.products` — `io.k2dv.garden.product.model.Product`

| Field | Constraint | Notes |
|---|---|---|
| title | not null | |
| handle | unique, not null | URL slug |
| description | text, nullable | |
| vendor | nullable | |
| productType | nullable | |
| status | ProductStatus, not null | |
| featuredImageId | UUID, nullable | FK to blob_objects |
| metaTitle, metaDescription | nullable | SEO |
| metadata | jsonb, nullable | |
| deletedAt | Instant, nullable | soft delete |
| tags | ManyToMany → ProductTag | |

**ProductStatus enum:** `DRAFT` · `ACTIVE` · `ARCHIVED`

---

### ProductVariant
`catalog.product_variants` — `io.k2dv.garden.product.model.ProductVariant`

| Field | Constraint | Notes |
|---|---|---|
| productId | not null, FK | |
| title | not null | |
| sku | unique, nullable | |
| barcode | nullable | |
| fulfillmentType | FulfillmentType, not null | |
| inventoryPolicy | InventoryPolicy, not null | |
| leadTimeDays | nullable | for PRE_ORDER/MADE_TO_ORDER |
| price | NUMERIC(19,4), not null | |
| compareAtPrice | NUMERIC(19,4), nullable | |
| weight | nullable | |
| weightUnit | nullable | |
| minimumOrderQty | not null, default 1 | |
| deletedAt | Instant, nullable | soft delete |
| optionValues | ManyToMany → ProductOptionValue | |

**FulfillmentType enum:** `IN_STOCK` · `PRE_ORDER` · `MADE_TO_ORDER`  
**InventoryPolicy enum:** `DENY` · `CONTINUE`

---

### ProductOption, ProductOptionValue
`catalog.product_options`, `catalog.product_option_values`

- Option: `productId`, `name`, `position`
- OptionValue: `optionId`, `value`

---

### ProductImage
`catalog.product_images` — `io.k2dv.garden.product.model.ProductImage`

- `productId`, `blobId`, `position`, `alt`

---

### ProductTag
`catalog.product_tags` — shared tag entity linked to products via join table.

---

## API — Storefront Products (`/api/v1/products`)

No auth required unless noted.

| Method | Path | Description |
|---|---|---|
| GET | `/` | List active products (paginated). Filters: `titleContains`, `vendor`, `productType`, `sortBy`, `companyId` |
| GET | `/{handle}` | Get product by handle. `companyId` optional — resolves B2B prices if provided |
| GET | `/variants/lookup?sku=` | Lookup variant by SKU |
| GET | `/{handle}/tiers` | B2B price tier list (`@Authenticated`, B2B context) |

---

## API — Admin Products (`/api/v1/admin/products`)

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `product:read` | List products (filterable, paginated) |
| POST | `/` | `product:write` | Create product |
| GET | `/{id}` | `product:read` | Get product |
| PUT | `/{id}` | `product:write` | Update product |
| DELETE | `/{id}` | `product:delete` | Soft-delete product |
| POST | `/{id}/variants` | `product:write` | Add variant |
| PUT | `/{id}/variants/{variantId}` | `product:write` | Update variant |
| DELETE | `/{id}/variants/{variantId}` | `product:delete` | Soft-delete variant |
| POST | `/{id}/images` | `product:write` | Upload image (blob) |
| PUT | `/{id}/images/reorder` | `product:write` | Reorder images |
| DELETE | `/{id}/images/{imageId}` | `product:delete` | Delete image |
| GET | `/{id}/options` | `product:read` | List options |
| POST | `/{id}/options` | `product:write` | Add option |
| PUT | `/{id}/options/{optionId}` | `product:write` | Update option |
| DELETE | `/{id}/options/{optionId}` | `product:delete` | Delete option |
| POST | `/{id}/tags` | `product:write` | Add tag |
| DELETE | `/{id}/tags/{tag}` | `product:delete` | Remove tag |

---

## Collections

### Collection
`catalog.collections` — `io.k2dv.garden.collection.model.Collection`

| Field | Constraint | Notes |
|---|---|---|
| title | not null | |
| handle | unique, not null | |
| description | text, nullable | |
| collectionType | CollectionType, not null | MANUAL or AUTOMATED |
| status | CollectionStatus, not null | |
| featuredImageId | UUID, nullable | |
| disjunctive | boolean | false = AND rules, true = OR rules |
| metaTitle, metaDescription | nullable | |
| deletedAt | soft delete | |

**CollectionType enum:** `MANUAL` · `AUTOMATED`  
**CollectionStatus enum:** `DRAFT` · `PUBLISHED` · `ARCHIVED`

---

### CollectionRule
`catalog.collection_rules`

- `collectionId`, `field` (CollectionRuleField), `operator` (CollectionRuleOperator), `value`

AUTOMATED collections re-evaluate membership when products are saved.

---

## API — Collections

*Admin (`/api/v1/admin/collections`):*

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `collection:read` | List collections |
| POST | `/` | `collection:write` | Create |
| GET | `/{id}` | `collection:read` | Get |
| PUT | `/{id}` | `collection:write` | Update |
| DELETE | `/{id}` | `collection:delete` | Soft-delete |
| POST | `/{id}/products` | `collection:write` | Add product (MANUAL only) |
| DELETE | `/{id}/products/{productId}` | `collection:delete` | Remove product (MANUAL only) |

*Storefront (`/api/v1/collections`, no auth):*

| Method | Path | Description |
|---|---|---|
| GET | `/` | List published collections |
| GET | `/{handle}` | Get published collection with products |

---

## Reviews

### ProductReview
`catalog.product_reviews` — `io.k2dv.garden.review.model.ProductReview`

| Field | Constraint | Notes |
|---|---|---|
| productId | not null | |
| userId | not null | |
| rating | short, not null | 1–5 |
| title | nullable | |
| body | text, nullable | |
| verifiedPurchase | boolean, default false | set if user has a paid order containing this product |
| status | ReviewStatus, not null | |

**ReviewStatus enum:** `PUBLISHED` · `HIDDEN`

---

## API — Reviews

*Storefront (`/api/v1/reviews`, `@Authenticated` for write):*

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/products/{handle}` | none | List published reviews for product |
| POST | `/products/{handle}` | `@Authenticated` | Submit review; verifiedPurchase auto-set |

*Admin (`/api/v1/admin/reviews`):*

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `review:read` | List all reviews (filterable by status, productId) |
| PUT | `/{id}/publish` | `review:write` | Set PUBLISHED |
| PUT | `/{id}/hide` | `review:write` | Set HIDDEN |
| DELETE | `/{id}` | `review:delete` | Delete review |
