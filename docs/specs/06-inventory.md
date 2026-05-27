# Inventory Spec

**Status:** Current  
**Last updated:** 2026-05-26  
**Package:** `io.k2dv.garden.inventory`  
**DB schema:** `inventory`

---

## Entities

### InventoryItem
`inventory.inventory_items`

| Field | Constraint | Notes |
|---|---|---|
| variantId | UUID, unique, not null | one item per variant |
| requiresShipping | boolean, default true | false for digital/virtual variants |

Created automatically when a variant is created.

---

### Location
`inventory.locations`

| Field | Constraint |
|---|---|
| name | not null |
| address | nullable |
| isActive | boolean |

---

### InventoryLevel
`inventory.inventory_levels`

| Field | Constraint | Notes |
|---|---|---|
| inventoryItem | ManyToOne | |
| location | ManyToOne | |
| quantityOnHand | int, not null | physical stock |
| quantityCommitted | int, not null | reserved for unpaid orders |
| lowStockAlertedAt | Instant, nullable | set when alert fired |

**Unique index:** `(inventory_item_id, location_id)`

**Available quantity** = `quantityOnHand − quantityCommitted`

---

### InventoryTransaction
`inventory.inventory_transactions` — immutable; extends `ImmutableBaseEntity`

| Field | Notes |
|---|---|
| inventoryItem | FK |
| location | FK |
| quantity | int (positive = in, negative = out) |
| reason | InventoryTransactionReason |
| note | text, nullable |

**InventoryTransactionReason enum:** `RECEIVED` · `SOLD` · `ADJUSTED` · `RETURNED` · `DAMAGED`

---

## API — Admin Inventory (`/api/v1/admin/inventory`)

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/items` | `inventory:read` | List inventory items (filterable by variantId, locationId) |
| GET | `/items/{id}` | `inventory:read` | Get item with levels |
| PUT | `/items/{id}/adjust` | `inventory:write` | Manual quantity adjustment (creates ADJUSTED transaction) |
| GET | `/items/{id}/transactions` | `inventory:read` | Transaction history |

---

## API — Admin Locations (`/api/v1/admin/locations`)

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `inventory:read` | List locations |
| POST | `/` | `inventory:write` | Create location |
| PUT | `/{id}` | `inventory:write` | Update location |
| DELETE | `/{id}` | `inventory:delete` | Delete location |
| GET | `/{id}/levels` | `inventory:read` | Stock levels at location |

---

## InventoryService Logic

**On order creation (reserve):**
- `quantityCommitted += qty` for each order item
- Creates SOLD transaction record

**On fulfillment creation (fulfill):**
- `quantityOnHand -= qty` for each fulfillment item
- `quantityCommitted -= qty` (de-commit)

**On fulfillment cancellation:**
- `quantityOnHand += qty` (return to stock)
- `quantityCommitted += qty` (re-commit, since order is still active)

**On return approval:**
- `quantityOnHand += qty`
- Creates RETURNED transaction

**InventoryPolicy enforcement (at cart/checkout):**
- `DENY` — blocks add-to-cart if `available < requested qty`
- `CONTINUE` — allows overselling; no block

**Low stock alerts:**
- Configured threshold per item
- `lowStockAlertedAt` set when level drops below threshold; cleared when restocked
- Alert sent via `AutomationScheduler` (see `08-infra.md`)
