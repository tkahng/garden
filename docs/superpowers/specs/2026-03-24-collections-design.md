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
| `collection_type` | ENUM `collection_type` | `MANUAL` or `AUTOMATED` |
| `status` | TEXT | `DRAFT` or `ACTIVE` — consistent with `catalog.products.status` (TEXT with CHECK constraint, not a PostgreSQL enum type) |
| `featured_image_id` | UUID | Nullable UUID (no DB-level FK constraint — consistent with `catalog.products` and `content.articles` patterns) |
| `disjunctive` | BOOLEAN | false = AND logic, true = OR logic (reserved for future multi-condition use, default false). Ignored for `MANUAL` collections. |
| `created_at` | TIMESTAMPTZ | DB-managed |
| `updated_at` | TIMESTAMPTZ | DB-managed via the existing `set_updated_at()` trigger (same trigger function used by products) |
| `deleted_at` | TIMESTAMPTZ | Soft delete |

**Indexes:** `handle`, `status`, `deleted_at` columns are indexed in the migration.

### `catalog.collection_rules`

Stores conditions for automated collections. Currently supports tag-based rules only; extensible to other fields later.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `collection_id` | UUID | FK → `catalog.collections` ON DELETE CASCADE |
| `field` | ENUM `collection_rule_field` | `TAG` (extensible: `VENDOR`, `PRODUCT_TYPE`, `PRICE`, etc.) |
| `operator` | ENUM `collection_rule_operator` | `EQUALS`, `NOT_EQUALS`, `CONTAINS` |
| `value` | TEXT | e.g. `"sale"` |
| `created_at` | TIMESTAMPTZ | DB-managed |

`GET /{id}/rules` returns rules ordered by `created_at ASC`. Evaluation order among rules is irrelevant because tag-based AND/OR logic produces the same result regardless of rule order. If multi-field rules are added in a future iteration and ordering becomes relevant, a `position` column can be added then.

### `catalog.collection_products`

Join table for collection-product membership. Supports explicit ordering.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | PK |
| `collection_id` | UUID | FK → `catalog.collections` ON DELETE CASCADE |
| `product_id` | UUID | FK → `catalog.products` ON DELETE CASCADE |
| `position` | INTEGER | Sort order within collection. No uniqueness constraint — gaps and ties are allowed. Position is always set verbatim (no row shifting). |
| `created_at` | TIMESTAMPTZ | DB-managed |

Unique constraint on `(collection_id, product_id)`.

**Position assignment:** When inserting a single product (manual add), `position = COALESCE(SELECT MAX(position) FROM collection_products WHERE collection_id = ?, 0) + 1`. This is a read-then-write; concurrent inserts may produce duplicate position values, which is acceptable since position is not unique-constrained and ordering ties are broken by `created_at ASC`. When inserting a batch (automated sync), new qualifying products are sorted by `product.created_at ASC` and assigned sequential positions starting from `COALESCE(max_existing_position, 0) + 1`.

**Indexes:** `collection_id`, `product_id` columns are indexed.

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
    │   ├── UpdateCollectionProductPositionRequest.java
    │   ├── CreateCollectionRuleRequest.java
    │   └── CollectionFilterRequest.java         — fields: type, status, title (substring)
    └── response/
        ├── AdminCollectionResponse.java          — full detail; used by admin POST / and GET /{id}; includes rules list and product count
        ├── AdminCollectionSummaryResponse.java   — list item; used by admin GET /; excludes rules and product list
        ├── CollectionDetailResponse.java         — storefront GET /{handle}; excludes rules
        ├── CollectionSummaryResponse.java        — storefront GET / list item
        ├── CollectionProductResponse.java        — shared between admin and storefront; includes position and product fields; does NOT include created_at
        └── CollectionRuleResponse.java           — used in GET /{id}/rules
```

### Service Responsibilities

- **`CollectionService`** — CRUD for collections, rules, and manual product membership. Delegates membership sync to `CollectionMembershipService`.
- **`CollectionMembershipService`** — Evaluates automated collection rules and syncs `collection_products`. Depends on `ProductRepository` directly (not `ProductService`) to avoid circular bean dependency. Called by `CollectionService` when rules change and by `ProductService` when a product's tags change (see Cross-Package Integration below).

---

## API Endpoints

### Admin (`/api/v1/admin/collections`)

| Method | Path | Description | Permission | Status | Response DTO |
|---|---|---|---|---|---|
| `POST` | `/` | Create collection | `collection:write` | `201 Created` | `AdminCollectionResponse` |
| `GET` | `/` | List with filters (type, status, title) | `collection:read` | `200 OK` | `PagedResult<AdminCollectionSummaryResponse>` |
| `GET` | `/{id}` | Get by ID | `collection:read` | `200 OK` | `AdminCollectionResponse` |
| `PATCH` | `/{id}` | Update collection | `collection:write` | `200 OK` | `AdminCollectionResponse` |
| `PATCH` | `/{id}/status` | Publish/unpublish | `collection:publish` | `200 OK` | `AdminCollectionResponse` |
| `DELETE` | `/{id}` | Soft delete | `collection:delete` | `204 No Content` | — |
| `GET` | `/{id}/products` | List products (paginated, ordered by position ASC, ties by created_at ASC) | `collection:read` | `200 OK` | `PagedResult<CollectionProductResponse>` |
| `POST` | `/{id}/products` | Add product to manual collection | `collection:write` | `201 Created` | `CollectionProductResponse` |
| `DELETE` | `/{id}/products/{productId}` | Remove product from manual collection | `collection:write` | `204 No Content` | — |
| `PATCH` | `/{id}/products/{productId}/position` | Set product position verbatim (no row shifting) | `collection:write` | `200 OK` | `CollectionProductResponse` |
| `GET` | `/{id}/rules` | List rules ordered by created_at ASC (bare list, not paginated) | `collection:read` | `200 OK` | `List<CollectionRuleResponse>` |
| `POST` | `/{id}/rules` | Add rule, triggers full membership sync | `collection:write` | `201 Created` | `CollectionRuleResponse` |
| `DELETE` | `/{id}/rules/{ruleId}` | Remove rule, triggers full membership sync | `collection:write` | `204 No Content` | — |

**Out of scope in this iteration:**
- Bulk reorder of all products in one request
- Atomic rule replacement (`PUT /{id}/rules`)
- Update an existing rule in place (`PATCH /{id}/rules/{ruleId}`) — correct a rule via delete + re-add

### Storefront (`/api/v1/collections`) — no authentication required, no filter parameters

| Method | Path | Description | Response DTO |
|---|---|---|---|
| `GET` | `/` | List ACTIVE collections, ordered by `created_at ASC` (paginated) | `PagedResult<CollectionSummaryResponse>` |
| `GET` | `/{handle}` | Get ACTIVE collection detail by handle | `CollectionDetailResponse` |
| `GET` | `/{handle}/products` | List ACTIVE products in ACTIVE collection, ordered by position ASC, ties by created_at ASC (paginated) | `PagedResult<CollectionProductResponse>` |

---

## Business Logic

### Automated Collection Membership Sync

The core non-trivial logic. Membership in `collection_products` for automated collections is always computed, never manually set.

**Trigger points:**
1. A rule is added to a collection → re-evaluate all products against the full updated ruleset
2. A rule is deleted from a collection → re-evaluate all products against the remaining ruleset
3. A product's tags are updated → re-evaluate all automated collections against that product
4. A product is soft-deleted **or set to `ARCHIVED`** → remove it from all `collection_products` rows (both manual and automated) via `removeProductFromAllCollections`. Storefront queries also filter via join for defense-in-depth.

**Empty ruleset:** An automated collection with no rules qualifies **no** products. No membership rows are created or retained.

**Evaluation logic:**
- `disjunctive = false` (AND): product qualifies if ALL rules match
- `disjunctive = true` (OR): product qualifies if ANY rule matches
- `disjunctive` is meaningless for `MANUAL` collections and is ignored in evaluation
- Tag rule operators (all case-insensitive):
  - `EQUALS` → product has a tag with name exactly matching `value`
  - `NOT_EQUALS` → product has no tag with name exactly matching `value`
  - `CONTAINS` → product has a tag whose name contains `value` as a substring

**Sync algorithm (`CollectionMembershipService`):**
1. Load current rules for the collection
2. If no rules exist, qualifying set = empty → remove all existing membership rows and return
3. Load all non-deleted products with their tags
4. Evaluate each product against the ruleset (AND or OR per `disjunctive`)
5. Compute: `toAdd = qualifyingIds - existingMemberIds`, `toRemove = existingMemberIds - qualifyingIds`
6. Delete `collection_products` rows for `toRemove`
7. Compute `max_existing_position` over the **surviving** rows (after removals). Insert `collection_products` rows for `toAdd`, sorted by `product.created_at ASC`, with positions `COALESCE(max_existing_position, 0) + 1, + 2, ...`
8. Entire operation wrapped in `@Transactional`

### Guard Rules

- **Duplicate add (manual):** adding a product already in the collection returns `409 Conflict`
- **Manual operations on automated collections:** attempting to manually add or remove products via API returns `400 Bad Request`
- **Remove non-member:** attempting to remove a product not in the collection returns `404 Not Found`
- **Handle uniqueness:** handle must be unique across non-deleted collections; uses same slugify logic as products
- **Soft-delete of a collection:** sets `deleted_at` on the collection row. `collection_products` and `collection_rules` rows are explicitly deleted by the application layer in `CollectionService` before setting `deleted_at` (DB `ON DELETE CASCADE` cannot fire on a soft delete). Membership rows are permanently removed. Note: the `ON DELETE CASCADE` on `collection_products.product_id` and `collection_products.collection_id` only fires on hard deletes, which never occur in this architecture — all deletes of products and collections are soft. Application-layer removal is the only mechanism.
- **`disjunctive` on MANUAL collections:** `CreateCollectionRequest` and `UpdateCollectionRequest` accept `disjunctive` for any collection type. For `MANUAL` collections, the value is stored as-is and silently ignored during evaluation. No validation error is raised.
- **Storefront visibility:** only `ACTIVE`, non-deleted collections returned; only `ACTIVE`, non-deleted products returned within a collection

### Permissions

New permissions to register in `auth.permissions`:
- `collection:read`
- `collection:write`
- `collection:publish`
- `collection:delete`

Storefront endpoints require no authentication.

---

## Cross-Package Integration

`ProductService` must call `CollectionMembershipService` after any operation that changes a product's tags. Specifically:

```java
// Called in ProductService after tags are updated, within the same transaction
collectionMembershipService.syncCollectionsForProduct(UUID productId, Set<String> newTagNames);
```

- `productId` — the product whose tags changed
- `newTagNames` — the full updated set of tag names (not a delta)
- This call is **synchronous** and participates in the caller's `@Transactional` context. `syncCollectionsForProduct` uses `@Transactional(propagation = REQUIRED)` so it joins the outer transaction.
- `CollectionMembershipService` is injected into `ProductService` via constructor injection

When a product is soft-deleted **or its status changes to `ARCHIVED`**, `ProductService` must also call:

```java
// Removes the product from both manual and automated collection_products rows
// @Transactional(propagation = REQUIRED) — joins the caller's transaction
collectionMembershipService.removeProductFromAllCollections(UUID productId);
```

**Avoiding circular dependency:** `CollectionMembershipService` depends on `CollectionRepository`, `CollectionRuleRepository`, `CollectionProductRepository`, and `ProductRepository` directly. It must NOT depend on `ProductService`.

---

## Database Migration

A new Flyway migration (V13) will:
1. Create `collection_type` enum type (`MANUAL`, `AUTOMATED`) in the `catalog` schema
2. Create `collection_rule_field` enum type (`TAG`) in the `catalog` schema
3. Create `collection_rule_operator` enum type (`EQUALS`, `NOT_EQUALS`, `CONTAINS`) in the `catalog` schema
4. Create `catalog.collections` table with `status TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'ACTIVE'))`, indexes on `handle`, `status`, `deleted_at`
5. Attach `set_updated_at()` trigger to `catalog.collections`
6. Create `catalog.collection_rules` table with `ON DELETE CASCADE` FK, `created_at` column; index on `collection_id`
7. Create `catalog.collection_products` table with `ON DELETE CASCADE` FKs and unique constraint on `(collection_id, product_id)`; indexes on `collection_id`, `product_id`
8. Register `collection:read`, `collection:write`, `collection:publish`, `collection:delete` permissions in `auth.permissions`
9. Grant permissions to appropriate roles

---

## Testing Strategy

- **Unit tests** for `CollectionMembershipService` rule evaluation and sync logic (no DB required):
  - AND logic: all rules must match; one failing rule disqualifies
  - OR logic: any rule matching qualifies (even though `disjunctive = true` is future scope, the unit path should be covered since the logic exists)
  - `EQUALS` operator (case-insensitive)
  - `NOT_EQUALS` operator (case-insensitive)
  - `CONTAINS` operator (case-insensitive substring)
  - Empty rule set → no products qualify
  - Sync diff: products to add and to remove are correctly computed

- **Integration tests** (`*IT.java`) for:
  - Collection CRUD via admin API (create, read, update, soft-delete)
  - Handle uniqueness enforcement (duplicate handle returns conflict)
  - Status transitions (DRAFT → ACTIVE, ACTIVE → DRAFT)
  - Manual product add (returns `CollectionProductResponse` with position)
  - Manual product remove (removes membership)
  - Duplicate add returns `409`
  - Remove non-member returns `404`
  - Position update sets position verbatim
  - Manual add/remove on automated collection returns `400`
  - Automated rule add triggers full membership sync
  - Automated rule remove triggers full membership sync
  - Product tag update triggers membership sync across all automated collections
  - Product soft-delete or ARCHIVED status removes from `collection_products` (both manual and automated)
  - Soft-delete of collection removes `collection_products` and `collection_rules` rows
  - `disjunctive = true` is stored without error (even though OR evaluation is not exercised in sync tests)
  - Storefront list returns only ACTIVE, non-deleted collections
  - Storefront `/{handle}` returns `404` for DRAFT or deleted collections
  - Storefront products endpoint returns only ACTIVE, non-deleted products ordered by position
  - Rules list returns bare list (not paginated)

---

## Future Extensions

- Multi-field automated rules (price, vendor, product type) — `collection_rule_field` enum and `disjunctive` flag already support this; only evaluation logic needs extension. A `position` column on `collection_rules` should be added at that point if rule order becomes significant.
- OR logic full activation (`disjunctive = true`) — schema and evaluation path already in place
- Bulk reorder endpoint for manual collections
- Atomic rule replacement (`PUT /{id}/rules`)
- In-place rule update (`PATCH /{id}/rules/{ruleId}`)
- Collection sort options (alphabetical, price, best-selling) on storefront
- Collection-level SEO metadata (meta title, meta description)
