# Inventory Domain Design Spec

**Date:** 2026-03-23
**Project:** `io.k2dv.garden`
**Domain:** `inventory`

---

## Goal

Build a multi-location inventory domain that tracks stock levels, records every stock movement as an immutable ledger entry, and exposes fulfillment intent (in-stock / pre-order / made-to-order) on product variants for storefront display.

---

## Architecture

Package-by-feature under `io.k2dv.garden.inventory`. Two services own distinct sub-domains: `LocationService` manages fulfillment locations, `InventoryService` manages stock levels and transactions. The product domain's `ProductVariant` entity is extended with three fulfillment fields. All list endpoints use offset pagination via Spring Data `Pageable` returning `PagedResult<T>` with `PageMeta`.

Follows the Shopify inventory model: fulfillment-intent fields live on `ProductVariant`, `InventoryItem` (1:1 with variant, already exists) is the physical-tracking bridge, `InventoryLevel` tracks quantity per `(inventoryItem, location)` pair, and `InventoryTransaction` is the immutable audit ledger.

**Tech Stack:** Spring Boot 4.x, Spring Data JPA, Hibernate 6, Flyway, Lombok, PostgreSQL, Testcontainers (IT), MockMvc (slice tests).

---

## Section 1: Data Model

### V12 Migration

#### New permissions (`location:read`, `location:write`)

`inventory:read` and `inventory:write` are already seeded in `V6__seed_iam_data.sql` (IDs `...000015`, `...000016`). V12 adds the two new location permissions:

```sql
INSERT INTO permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000025', 'location:read',  'location', 'read',  clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000026', 'location:write', 'location', 'write', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- STAFF, MANAGER, OWNER receive both location permissions
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r, permissions p
WHERE r.name IN ('STAFF', 'MANAGER', 'OWNER')
  AND p.name IN ('location:read', 'location:write')
ON CONFLICT DO NOTHING;
```

#### Modify `product_variants`

Add three columns to the existing `product_variants` table:

| Column | Type | Notes |
|---|---|---|
| `fulfillment_type` | TEXT NOT NULL DEFAULT `'IN_STOCK'` | `IN_STOCK` \| `PRE_ORDER` \| `MADE_TO_ORDER` |
| `inventory_policy` | TEXT NOT NULL DEFAULT `'DENY'` | `DENY` = stop selling at 0; `CONTINUE` = allow oversell |
| `lead_time_days` | INT NOT NULL DEFAULT `0` | Displayed to storefront for MTO/pre-order items |

#### New `locations` table

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` default |
| `name` | TEXT NOT NULL | Display name, e.g. "Main Warehouse", "Manufacturer" |
| `address` | TEXT | Optional free-text address |
| `is_active` | BOOLEAN NOT NULL DEFAULT TRUE | Soft-disable; not a full soft-delete |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |

#### New `inventory_levels` table

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` default |
| `inventory_item_id` | UUID NOT NULL FK → `inventory_items(id)` ON DELETE CASCADE | |
| `location_id` | UUID NOT NULL FK → `locations(id)` ON DELETE CASCADE | |
| `quantity_on_hand` | INT NOT NULL DEFAULT 0 | Current available stock |
| `quantity_committed` | INT NOT NULL DEFAULT 0 | Reserved for future cart-reservation feature |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | |
| `updated_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Trigger-managed |

UNIQUE constraint on `(inventory_item_id, location_id)`.

#### New `inventory_transactions` table

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` default |
| `inventory_item_id` | UUID NOT NULL FK → `inventory_items(id)` | |
| `location_id` | UUID NOT NULL FK → `locations(id)` | |
| `quantity` | INT NOT NULL | Positive = stock in, negative = stock out |
| `reason` | TEXT NOT NULL | `RECEIVED` \| `SOLD` \| `ADJUSTED` \| `RETURNED` \| `DAMAGED` |
| `note` | TEXT | Optional free-text explanation |
| `created_at` | TIMESTAMPTZ NOT NULL DEFAULT `clock_timestamp()` | Immutable; no `updated_at` |

Inventory transactions are immutable ledger rows — never updated or soft-deleted. Corrections are made with a new offsetting transaction.

---

## Section 2: Enums & Entities

### Enums (Java)

- `FulfillmentType`: `IN_STOCK`, `PRE_ORDER`, `MADE_TO_ORDER`
- `InventoryPolicy`: `DENY`, `CONTINUE`
- `InventoryTransactionReason`: `RECEIVED`, `SOLD`, `ADJUSTED`, `RETURNED`, `DAMAGED`

### Modified entity

**`ProductVariant`** (product domain) — add three fields:
```java
@Enumerated(EnumType.STRING)
@Column(name = "fulfillment_type", nullable = false)
private FulfillmentType fulfillmentType = FulfillmentType.IN_STOCK;

@Enumerated(EnumType.STRING)
@Column(name = "inventory_policy", nullable = false)
private InventoryPolicy inventoryPolicy = InventoryPolicy.DENY;

@Column(name = "lead_time_days", nullable = false)
private int leadTimeDays = 0;
```

Enums live in `io.k2dv.garden.inventory.model` and are imported into the product domain.

### New entities (inventory package)

- **`Location`** — maps to `locations`; extends `BaseEntity`; fields: `name`, `address`, `isActive`, `createdAt`, `updatedAt`
- **`InventoryLevel`** — maps to `inventory_levels`; extends `BaseEntity`; fields: `inventoryItem` (ManyToOne), `location` (ManyToOne), `quantityOnHand`, `quantityCommitted`, `createdAt`, `updatedAt`
- **`InventoryTransaction`** — maps to `inventory_transactions`; **does NOT extend `BaseEntity`** (which includes `updatedAt`, but `inventory_transactions` has no `updated_at` column). Instead extends a new **`ImmutableBaseEntity`** defined in `io.k2dv.garden.shared.model`:

```java
@MappedSuperclass
@Getter
public abstract class ImmutableBaseEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
```

`InventoryTransaction` fields: `inventoryItem` (ManyToOne), `location` (ManyToOne), `quantity`, `reason` (enum), `note`, `createdAt`.

---

## Section 3: Repositories

- `LocationRepository` extends `JpaRepository<Location, UUID>`
- `InventoryLevelRepository` extends `JpaRepository<InventoryLevel, UUID>`
  - Custom finder: `findByInventoryItemId(UUID inventoryItemId)`
  - Custom finder: `findByInventoryItemIdAndLocationId(UUID inventoryItemId, UUID locationId)`
- `InventoryTransactionRepository` extends `JpaRepository<InventoryTransaction, UUID>`
  - Custom finder: `findByInventoryItemIdAndLocationId(UUID inventoryItemId, UUID locationId, Pageable pageable)`
  - Custom finder: `findByInventoryItemId(UUID inventoryItemId, Pageable pageable)`

---

## Section 4: DTOs

### Requests

- `CreateLocationRequest(String name, String address)` — `@NotBlank name`
- `UpdateLocationRequest(String name, String address)` — all optional
- `ReceiveStockRequest(UUID locationId, int quantity, String note)` — `@Min(1) quantity`
- `AdjustStockRequest(UUID locationId, int delta, InventoryTransactionReason reason, String note)` — `delta` may be negative; validated at the service layer: `if (reason == RECEIVED || reason == SOLD) throw new BadRequestException("INVALID_REASON", "Use the receive endpoint for RECEIVED; SOLD is system-managed")`
- `UpdateVariantFulfillmentRequest(FulfillmentType fulfillmentType, InventoryPolicy inventoryPolicy, int leadTimeDays)`

### Responses

- `LocationResponse(UUID id, String name, String address, boolean isActive, OffsetDateTime createdAt, OffsetDateTime updatedAt)`
- `InventoryLevelResponse(UUID id, UUID inventoryItemId, UUID locationId, String locationName, int quantityOnHand, int quantityCommitted)`
- `InventoryTransactionResponse(UUID id, UUID inventoryItemId, UUID locationId, String locationName, int quantity, InventoryTransactionReason reason, String note, OffsetDateTime createdAt)`

### Storefront impact

`AdminVariantResponse` gains three new fields (updated record signature):

```java
public record AdminVariantResponse(
    UUID id,
    String title,
    String sku,
    String barcode,
    BigDecimal price,
    BigDecimal compareAtPrice,
    BigDecimal weight,
    String weightUnit,
    List<OptionValueLabel> optionValues,
    FulfillmentType fulfillmentType,
    InventoryPolicy inventoryPolicy,
    int leadTimeDays,
    Instant deletedAt
) {}
```

`ProductDetailResponse` variant shape also gains `fulfillmentType`, `inventoryPolicy`, `leadTimeDays`.

---

## Section 5: Services

### `LocationService`

- `create(CreateLocationRequest)` → `LocationResponse`
- `list()` → `List<LocationResponse>` — returns **all** locations regardless of `isActive` (admin needs to see inactive locations to reactivate them); small list, no pagination needed
- `get(UUID id)` → `LocationResponse` (throws `NotFoundException`)
- `update(UUID id, UpdateLocationRequest)` → `LocationResponse`
- `deactivate(UUID id)` — sets `isActive = false`
- `reactivate(UUID id)` — sets `isActive = true`

### `InventoryService`

- `getLevels(UUID variantId)` → `List<InventoryLevelResponse>` — loads `InventoryItem` by variantId, returns all `InventoryLevel` rows across locations
- `receiveStock(UUID variantId, ReceiveStockRequest)` → `InventoryLevelResponse` — upserts `InventoryLevel`, increments `quantityOnHand`, creates `RECEIVED` transaction
- `adjustStock(UUID variantId, AdjustStockRequest)` → `InventoryLevelResponse` — applies signed `delta` to `quantityOnHand`, creates transaction with given reason; throws `BadRequestException` if `quantityOnHand` would go below 0 and `inventoryPolicy = DENY`
- `listTransactions(UUID variantId, UUID locationId, Pageable pageable)` → `PagedResult<InventoryTransactionResponse>` — paginated ledger; `locationId` is optional (null = all locations for variant)
- `updateVariantFulfillment(UUID variantId, UpdateVariantFulfillmentRequest)` → `AdminVariantResponse`

---

## Section 6: Controllers & Permissions

### Permissions

`inventory:read` and `inventory:write` are already seeded in `V6`. V12 adds `location:read` (`...000025`) and `location:write` (`...000026`) — see Section 1 for the exact SQL.

### `AdminLocationController` — `/api/v1/admin/locations`

| Method | Path | Permission | Returns |
|---|---|---|---|
| `POST` | `/` | `location:write` | 201 + `LocationResponse` |
| `GET` | `/` | `location:read` | 200 + `List<LocationResponse>` (all, including inactive) |
| `GET` | `/{id}` | `location:read` | 200 + `LocationResponse` |
| `PATCH` | `/{id}` | `location:write` | 200 + `LocationResponse` |
| `DELETE` | `/{id}` | `location:write` | 204 (deactivates — sets `isActive = false`) |
| `POST` | `/{id}/reactivate` | `location:write` | 200 + `LocationResponse` |

### `AdminInventoryController` — `/api/v1/admin/inventory`

| Method | Path | Permission | Returns |
|---|---|---|---|
| `GET` | `/variants/{variantId}/levels` | `inventory:read` | 200 + `List<InventoryLevelResponse>` |
| `POST` | `/variants/{variantId}/receive` | `inventory:write` | 200 + `InventoryLevelResponse` |
| `POST` | `/variants/{variantId}/adjust` | `inventory:write` | 200 + `InventoryLevelResponse` |
| `GET` | `/variants/{variantId}/transactions` | `inventory:read` | 200 + `PagedResult<InventoryTransactionResponse>` |
| `PATCH` | `/variants/{variantId}/fulfillment` | `inventory:write` | 200 + `AdminVariantResponse` |

No storefront inventory endpoints — the storefront uses `inventoryPolicy` + `fulfillmentType` on the variant response to decide purchase eligibility and display.

---

## Section 7: Testing

### Integration Tests (`AbstractIntegrationTest`)

**`LocationServiceIT`** (4 tests):
- `createLocation_returnsResponse`
- `updateLocation_changesFields`
- `deactivateLocation_setsIsActiveFalse`
- `getLocation_notFound_throwsNotFoundException`

**`InventoryServiceIT`** (8 tests):
- `receiveStock_newLevel_createsLevelAndTransaction`
- `receiveStock_existingLevel_incrementsQty`
- `adjustStock_negativeAllowed_whenPolicyContinue`
- `adjustStock_belowZero_deny_throwsBadRequest`
- `getLevels_returnsAllLocations`
- `listTransactions_filterByLocation_returnsPaged`
- `updateVariantFulfillment_persistsAllThreeFields`
- `listTransactions_allLocations_whenLocationIdNull`

### Controller Slice Tests (`@WebMvcTest`)

**`AdminLocationControllerTest`** (5 tests):
- `createLocation_validRequest_returns201`
- `createLocation_missingName_returns400`
- `getLocation_notFound_returns404`
- `deactivateLocation_returns204`
- `reactivateLocation_returns200`

**`AdminInventoryControllerTest`** (5 tests):
- `receiveStock_validRequest_returns200`
- `adjustStock_validRequest_returns200`
- `adjustStock_invalidReason_returns400`
- `getLevels_returns200`
- `listTransactions_returns200WithPage`

---

## Section 8: Future Extensions

The following are explicitly deferred and require no redesign:

- **Cart reservation**: `quantityCommitted` column is already present. Add `RESERVED` / `RESERVATION_RELEASED` transaction reasons and a reservation expiry job.
- **Location transfers**: Add `TRANSFER_OUT` / `TRANSFER_IN` transaction reason pair and a `POST /variants/{variantId}/transfer` endpoint.
- **Low-stock alerts**: Query `InventoryLevel` where `quantityOnHand ≤ threshold`.
- **Cost tracking**: Add `cost` to `InventoryItem` (already the right place per Shopify model).
