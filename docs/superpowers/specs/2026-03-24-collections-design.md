# Collections Feature Design

**Date:** 2026-03-24
**Status:** Approved

## Overview

Implement a Shopify-style Collections feature for the Garden e-commerce backend. Collections group products for storefront navigation and merchandising. Two types are supported: **Manual** (explicit membership) and **Automated** (rule-based membership). Collections are flat (no nesting).

---

## Data Model

### `catalog.collections`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK, `gen_random_uuid()` |
| `title` | TEXT | Required |
| `handle` | TEXT | Unique, URL-friendly slug |
| `description` | TEXT | Nullable |
| `collection_type` | ENUM | `MANUAL` or `AUTOMATED` |
| `status` | ENUM | `DRAFT` or `ACTIVE` |
| `featured_image_id` | UUID | FK → `storage.objects`, nullable |
| `disjunctive` | BOOLEAN | false = AND logic, true = OR logic (reserved for future multi-condition use, default false) |
| `created_at` | TIMESTAMPTZ | DB-managed |
| `updated_at` | TIMESTAMPTZ | DB-managed via trigger |
| `deleted_at` | TIMESTAMPTZ | Soft delete |

### `catalog.collection_rules`

Stores conditions for automated collections. Currently supports tag-based rules only; extensible to other fields later.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `collection_id` | UUID | FK → `catalog.collections` |
| `field` | ENUM | `TAG` (extensible: `VENDOR`, `PRODUCT_TYPE`, `PRICE`, etc.) |
| `operator` | ENUM | `EQUALS`, `NOT_EQUALS`, `CONTAINS` |
| `value` | TEXT | e.g. `"sale"` |

### `catalog.collection_products`

Join table for collection-product membership. Supports explicit ordering.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `collection_id` | UUID | FK → `catalog.collections` |
| `product_id` | UUID | FK → `catalog.products` |
| `position` | INTEGER | Sort order within collection |
| `created_at` | TIMESTAMPTZ | DB-managed |

Unique constraint on `(collection_id, product_id)`.

---

## Package Structure

```
io.k2dv.garden.collection/
├── controller/
│   ├── AdminCollectionController.java
│   └── StorefrontCollectionController.java
├── model/
│   ├── Collection.java
│   ├── CollectionRule.java
│   └── CollectionProduct.java
├── repository/
│   ├── CollectionRepository.java
│   ├── CollectionRuleRepository.java
│   └── CollectionProductRepository.java
├── service/
│   ├── CollectionService.java
│   └── CollectionMembershipService.java
├── specification/
│   └── CollectionSpecification.java
└── dto/
    ├── request/
    │   ├── CreateCollectionRequest.java
    │   ├── UpdateCollectionRequest.java
    │   ├── CollectionStatusRequest.java
    │   ├── AddCollectionProductRequest.java
    │   └── CollectionFilterRequest.java
    └── response/
        ├── AdminCollectionResponse.java
        ├── CollectionSummaryResponse.java
        ├── CollectionDetailResponse.java
        └── CollectionProductResponse.java
```

### Service Responsibilities

- **`CollectionService`** — CRUD for collections, rules, and manual product membership. Delegates membership sync to `CollectionMembershipService`.
- **`CollectionMembershipService`** — Evaluates automated collection rules and syncs `collection_products`. Called by `CollectionService` when rules change and by `ProductService` when a product's tags change.

---

## API Endpoints

### Admin (`/api/v1/admin/collections`)

| Method | Path | Description | Permission |
|---|---|---|---|
| `POST` | `/` | Create collection | `collection:write` |
| `GET` | `/` | List with filters (type, status, title) | `collection:read` |
| `GET` | `/{id}` | Get by ID | `collection:read` |
| `PATCH` | `/{id}` | Update collection | `collection:write` |
| `PATCH` | `/{id}/status` | Publish/unpublish | `collection:publish` |
| `DELETE` | `/{id}` | Soft delete | `collection:delete` |
| `GET` | `/{id}/products` | List products in collection (paginated) | `collection:read` |
| `POST` | `/{id}/products` | Add product to manual collection | `collection:write` |
| `DELETE` | `/{id}/products/{productId}` | Remove product from manual collection | `collection:write` |
| `PATCH` | `/{id}/products/{productId}/position` | Update product position | `collection:write` |
| `GET` | `/{id}/rules` | List rules for automated collection | `collection:read` |
| `POST` | `/{id}/rules` | Add rule | `collection:write` |
| `DELETE` | `/{id}/rules/{ruleId}` | Remove rule | `collection:write` |

### Storefront (`/api/v1/collections`) — no authentication required

| Method | Path | Description |
|---|---|---|
| `GET` | `/` | List ACTIVE collections (paginated) |
| `GET` | `/{handle}` | Get collection detail by handle |
| `GET` | `/{handle}/products` | List ACTIVE products in collection (paginated) |

---

## Business Logic

### Automated Collection Membership Sync

The core non-trivial logic. Membership in `collection_products` for automated collections is always computed, never manually set.

**Trigger points:**
1. A collection's rules are created or deleted → re-evaluate all products against the updated ruleset
2. A product's tags are updated → re-evaluate all automated collections against that product

**Evaluation logic:**
- `disjunctive = false` (AND): product qualifies if ALL rules match
- `disjunctive = true` (OR): product qualifies if ANY rule matches
- Tag rule match: check if the product's tag set satisfies the operator (`EQUALS` → exact tag match, `NOT_EQUALS` → tag not present, `CONTAINS` → tag name contains substring)

**Sync algorithm (`CollectionMembershipService`):**
1. Compute qualifying product IDs based on rules
2. Fetch existing `collection_products` for the collection
3. Insert rows for newly qualifying products (`position = max_position + 1`)
4. Delete rows for products that no longer qualify
5. Entire operation wrapped in `@Transactional`

### Guard Rules

- **Manual collections:** adding a product already in the collection returns `409 Conflict`
- **Automated collections:** attempting to manually add/remove products via API returns `400 Bad Request` — membership is rule-driven only
- **Handle uniqueness:** handle must be unique across non-deleted collections; uses same slugify logic as products
- **Cascade deletes:** soft-deleting a collection removes `collection_products` and `collection_rules` rows (hard delete of join rows, soft delete of the collection itself)
- **Storefront visibility:** only `ACTIVE` collections are returned; only `ACTIVE` products are returned within a collection

### Permissions

New permissions to register in `auth.permissions`:
- `collection:read`
- `collection:write`
- `collection:publish`
- `collection:delete`

Storefront endpoints require no authentication.

---

## Database Migration

A new Flyway migration (next version after V12) will:
1. Create `collection_type` and `collection_status` enum types in the `catalog` schema
2. Create `catalog.collections` table
3. Create `catalog.collection_rules` table
4. Create `catalog.collection_products` table with unique constraint
5. Register `collection:*` permissions in `auth.permissions`
6. Grant permissions to appropriate roles

---

## Testing Strategy

- **Unit tests** for `CollectionMembershipService` rule evaluation logic (no DB required)
- **Integration tests** (`*IT.java`) for:
  - Collection CRUD via admin API
  - Manual product add/remove/reorder
  - Automated rule add/remove triggers membership sync
  - Product tag update triggers membership sync
  - Storefront visibility (only ACTIVE collections + products)
  - Guard rules (manual add to automated collection, duplicate membership)

---

## Future Extensions

- Multi-field automated rules (price, vendor, product type) — `disjunctive` flag and `collection_rules` schema already support this
- Collection sort options (alphabetical, price, best-selling) on storefront
- Collection-level SEO metadata (meta title, meta description)
