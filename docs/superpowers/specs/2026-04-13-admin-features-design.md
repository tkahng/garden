# Admin Features Design Spec

**Date:** 2026-04-13
**Project:** `io.k2dv.garden`
**Branch:** `feat/admin`

---

## Overview

This spec covers nine missing backend APIs identified in the admin API review. Tax configuration is excluded — handled entirely by Stripe. All new endpoints follow existing project conventions: package-by-feature, UUIDv7 PKs, `clock_timestamp()` timestamps, `@Generated` on BaseEntity, `PUT` (no `PATCH`), offset pagination for admin lists, `@HasPermission` for auth, `PagedResult<T>` + `ApiResponse<T>` wrappers.

**Features:**

1. Analytics / Dashboard stats
2. Discounts (full CRUD)
3. Shipping zones and rates
4. Admin-side refunds
5. Order fulfillment
6. File manager list (`GET /admin/blobs`)
7. Customer notes and tags
8. Gift cards (full CRUD)
9. Order timeline / notes (events)

---

## New Permissions

Add to `V19__seed_admin_permissions.sql`:

| Permission | Resource | Action | Role (min) |
|---|---|---|---|
| `order:read` | `order` | `read` | MANAGER |
| `order:write` | `order` | `write` | MANAGER |
| `discount:read` | `discount` | `read` | MANAGER |
| `discount:write` | `discount` | `write` | MANAGER |
| `discount:delete` | `discount` | `delete` | MANAGER |
| `shipping:read` | `shipping` | `read` | MANAGER |
| `shipping:write` | `shipping` | `write` | MANAGER |
| `shipping:delete` | `shipping` | `delete` | MANAGER |
| `gift_card:read` | `gift_card` | `read` | MANAGER |
| `gift_card:write` | `gift_card` | `write` | MANAGER |
| `gift_card:delete` | `gift_card` | `delete` | MANAGER |
| `stats:read` | `stats` | `read` | STAFF |
| `blob:read` | `blob` | `read` | STAFF |
| `blob:upload` | `blob` | `upload` | STAFF |
| `blob:delete` | `blob` | `delete` | STAFF |

`blob:upload` and `blob:delete` already exist in code — add to migration for completeness. `order:read` / `order:write` are used by existing `AdminOrderController` — add to migration to formalize.

---

## 1. Analytics / Dashboard Stats

### Package

`io.k2dv.garden.stats`

### Design

No new entities. Pure aggregation queries over `checkout.orders` and `auth.users`. The stats endpoint takes an optional date range (`from`, `to`); defaults to all time.

```
StatsResponse
  ├─ revenue          (BigDecimal — sum of totalAmount for PAID + PARTIALLY_FULFILLED + FULFILLED + REFUNDED orders)
  ├─ orderCount       (long — total orders, any status)
  ├─ paidOrderCount   (long — PAID | PARTIALLY_FULFILLED | FULFILLED)
  ├─ customerCount    (long — distinct users who have at least one order)
  ├─ aov              (BigDecimal — revenue / paidOrderCount, null if zero)
  ├─ from             (Instant, nullable — echoed from request)
  └─ to               (Instant, nullable — echoed from request)
```

### API Surface

```
GET /api/v1/admin/stats                  (stats:read)
    ?from={iso8601}&to={iso8601}         (optional)
```

### Package Structure

```
io.k2dv.garden.stats
  ├─ dto/   StatsResponse
  ├─ service/ StatsService
  └─ controller/ AdminStatsController
```

---

## 2. Discounts

### Package

`io.k2dv.garden.discount`

### Data Model

```
Discount
  ├─ id (UUIDv7)
  ├─ code           (VARCHAR 64, unique, stored UPPERCASE)
  ├─ type           (PERCENTAGE | FIXED_AMOUNT | FREE_SHIPPING)
  ├─ value          (NUMERIC(19,4) — percentage 0–100 or fixed amount; 0 for FREE_SHIPPING)
  ├─ minOrderAmount (NUMERIC(19,4), nullable)
  ├─ maxUses        (INTEGER, nullable — null = unlimited)
  ├─ usedCount      (INTEGER NOT NULL DEFAULT 0)
  ├─ startsAt       (TIMESTAMPTZ, nullable)
  ├─ endsAt         (TIMESTAMPTZ, nullable)
  ├─ isActive       (BOOLEAN NOT NULL DEFAULT TRUE)
  ├─ createdAt, updatedAt
```

Schema: `checkout`. Table: `discounts`.

**Validation rules (service layer):**
- `code` normalized to uppercase on create/update
- `type = PERCENTAGE` → `value` must be 1–100
- `type = FIXED_AMOUNT` → `value` must be > 0
- `type = FREE_SHIPPING` → `value` is ignored / stored as 0
- `endsAt` must be after `startsAt` if both provided

### Discount Application at Checkout

Discounts are applied in `CartService` (cart-based checkout) and in `QuoteService` (quote-based checkout). The flow:

1. Customer submits a `code` alongside the checkout request
2. `DiscountService.redeem(String code, BigDecimal orderAmount)` validates and atomically increments `usedCount`:
   - Discount must be `isActive = true`
   - Current time must be within `[startsAt, endsAt]` if set
   - `orderAmount >= minOrderAmount` if set
   - **Atomic increment:** `UPDATE discounts SET used_count = used_count + 1 WHERE id = ? AND (max_uses IS NULL OR used_count < max_uses)` — if 0 rows updated, the discount is exhausted; throw `ConflictException("DISCOUNT_EXHAUSTED")`
   - Returns a `DiscountApplication` value object: `{ discountId, type, value, discountedAmount }`
3. The calling service applies the `discountedAmount` to reduce the order total before passing to Stripe
4. The `discountId` is stored on the `Order` (see Order model change below)

**`usedCount` concurrency:** The atomic UPDATE with the `used_count < max_uses` guard prevents overshooting `maxUses` under concurrent load. No `SELECT FOR UPDATE` or application-level locking needed.

**Order model change:** Add `discountId` (UUID, nullable FK → `checkout.discounts`) and `discountAmount` (NUMERIC(19,4), nullable) to the `orders` table. Added in the same `V20__create_discounts.sql` migration (ALTER TABLE).

### Storefront Discount Validation

A lightweight endpoint for the checkout UI to validate a code before submission — does not increment `usedCount`.

```
GET /api/v1/storefront/discounts/validate    (authenticated)
    ?code=SAVE10&orderAmount=150.00
```

Response:
```json
{
  "data": {
    "valid": true,
    "type": "PERCENTAGE",
    "value": 10,
    "discountedAmount": 15.00,
    "message": "10% off applied"
  }
}
```

If invalid: `valid: false` with a `reason` field (`INVALID_CODE`, `EXPIRED`, `MIN_ORDER_NOT_MET`, `EXHAUSTED`). Returns HTTP 200 in all cases — validation result is in the body.

### API Surface

```
// Admin
GET    /api/v1/admin/discounts                   (discount:read)
POST   /api/v1/admin/discounts                   (discount:write)
GET    /api/v1/admin/discounts/{id}              (discount:read)
PUT    /api/v1/admin/discounts/{id}              (discount:write)
DELETE /api/v1/admin/discounts/{id}              (discount:delete)

// Storefront
GET    /api/v1/storefront/discounts/validate     (authenticated)
       ?code=&orderAmount=
```

Admin GET list filters: `?code=`, `?type=`, `?isActive=`, `?page=`, `?size=`

### Package Structure

```
io.k2dv.garden.discount
  ├─ model/        Discount, DiscountType
  ├─ repository/   DiscountRepository
  ├─ service/      DiscountService
  ├─ dto/          CreateDiscountRequest, UpdateDiscountRequest, DiscountResponse,
  │                DiscountFilter, DiscountApplication, DiscountValidationResponse
  ├─ specification/ DiscountSpecification
  └─ controller/   AdminDiscountController, StorefrontDiscountController
```

---

## 3. Shipping Zones and Rates

### Package

`io.k2dv.garden.shipping`

### Design

Zones are defined by country + province/state selection — the same model Shopify uses. No geographic extensions, no geocoding, no new dependencies. Zone matching on checkout compares the delivery address `country` (ISO 3166-1 alpha-2) and optional `province` (ISO 3166-2 subdivision code, e.g. `US-NY`) against the zone's arrays using PostgreSQL `= ANY(column)`.

A null `countryCodes` array means the zone matches all countries (useful for a catch-all "Rest of World" zone). A null `provinces` array within a country means the zone covers the entire country.

### Data Model

```
ShippingZone
  ├─ id (UUIDv7)
  ├─ name           (VARCHAR 128)
  ├─ description    (TEXT, nullable)
  ├─ countryCodes   (TEXT[], nullable — ISO 3166-1 alpha-2; null = worldwide)
  ├─ provinces      (TEXT[], nullable — ISO 3166-2 codes e.g. "US-NY"; null = all provinces)
  ├─ isActive       (BOOLEAN NOT NULL DEFAULT TRUE)
  ├─ createdAt, updatedAt
  └─< ShippingRate
        ├─ id (UUIDv7)
        ├─ zoneId (FK → ShippingZone)
        ├─ name              (VARCHAR 128 — e.g. "Standard", "Express")
        ├─ price             (NUMERIC(19,4) NOT NULL — 0 = free)
        ├─ minWeightGrams    (INTEGER, nullable)
        ├─ maxWeightGrams    (INTEGER, nullable)
        ├─ minOrderAmount    (NUMERIC(19,4), nullable)
        ├─ estimatedDaysMin  (INTEGER, nullable)
        ├─ estimatedDaysMax  (INTEGER, nullable)
        ├─ carrier           (VARCHAR 64, nullable — "UPS", "FedEx", etc.)
        ├─ isActive          (BOOLEAN NOT NULL DEFAULT TRUE)
        ├─ createdAt, updatedAt
```

Schema: `shipping`. Tables: `shipping_zones`, `shipping_rates`.

### Zone Matching Logic (service layer)

Given a delivery address with `country` + `province`:

1. Find all active zones where `countryCodes IS NULL` OR `country = ANY(country_codes)`
2. From those, further filter: `provinces IS NULL` OR `province = ANY(provinces)`
3. Return all active rates from matching zones, sorted by price ascending

Pure JPQL/SQL — no extensions needed.

### Zone Conflict Resolution

An address may match multiple zones (e.g. a "US" zone and a "US-NY" zone). Rule: **return rates from all matching zones**, let the customer choose. This mirrors Shopify's behaviour — the most specific zone is not auto-selected, all applicable options are surfaced. The storefront UI presents the full list sorted by price ascending.

### API Surface

```
// Admin — zone management
GET    /api/v1/admin/shipping/zones                          (shipping:read)
POST   /api/v1/admin/shipping/zones                         (shipping:write)
GET    /api/v1/admin/shipping/zones/{id}                    (shipping:read)
PUT    /api/v1/admin/shipping/zones/{id}                    (shipping:write)
DELETE /api/v1/admin/shipping/zones/{id}                    (shipping:delete)

// Admin — rate management (nested under zone)
GET    /api/v1/admin/shipping/zones/{id}/rates              (shipping:read)
POST   /api/v1/admin/shipping/zones/{id}/rates              (shipping:write)
PUT    /api/v1/admin/shipping/zones/{id}/rates/{rateId}     (shipping:write)
DELETE /api/v1/admin/shipping/zones/{id}/rates/{rateId}     (shipping:delete)

// Storefront — rate lookup (authenticated)
GET    /api/v1/storefront/shipping/rates
       ?country={ISO2}&province={ISO3166-2}&orderAmount={decimal}
```

The storefront passes the delivery address `country` and `province` directly — no geocoding required.

### Package Structure

```
io.k2dv.garden.shipping
  ├─ model/        ShippingZone, ShippingRate
  ├─ repository/   ShippingZoneRepository, ShippingRateRepository
  ├─ service/      ShippingService
  ├─ dto/          CreateShippingZoneRequest, UpdateShippingZoneRequest, ShippingZoneResponse,
  │                CreateShippingRateRequest, UpdateShippingRateRequest, ShippingRateResponse,
  │                ShippingZoneSummaryResponse, ShippingRateLookupRequest
  └─ controller/   AdminShippingController, StorefrontShippingController
```

---

## 4. Admin-Side Refunds and Order Updates

### Admin Refund

Reuse `OrderService.refundOrder()` but remove the ownership check. Admin can refund any PAID order. Add a new service method `adminRefundOrder(UUID orderId)` that skips the userId equality check.

Emits an `OrderEvent` of type `REFUND_ISSUED` (see Section 9).

**Partial refunds — known limitation:** This implementation issues a full refund via Stripe (`RefundCreateParams` with no `amount` set). Partial refunds (by line item or custom amount) are out of scope. If partial refunds are needed in the future, `POST /admin/orders/{id}/refund` can accept an optional `amount` body field and pass it to Stripe's `setAmount()`.

### API Surface

```
POST /api/v1/admin/orders/{id}/refund    (order:write)
```

No request body. Returns `OrderResponse`.

### Admin Order Update

Admins occasionally need to update mutable fields on an order without changing its status: internal notes, shipping address (before fulfillment), or the assigned discount.

Add `adminNotes` and `shippingAddress` (JSON snapshot) to the `Order` entity and table (via `V22__create_fulfillments.sql` or a separate migration). Only fields present in the request body are updated — `PUT` semantics on the sub-resource, not the whole order.

```
PUT /api/v1/admin/orders/{id}    (order:write)
```

Request body:
```json
{
  "adminNotes": "Customer requested gift wrap.",
  "shippingAddress": {
    "firstName": "Jane", "lastName": "Doe",
    "address1": "123 Main St", "city": "Portland",
    "province": "US-OR", "zip": "97201", "country": "US"
  }
}
```

Returns updated `OrderResponse`. Status transitions (cancel, refund, fulfillment) remain on their dedicated endpoints and are not allowed through this endpoint.

### Changes

- Add `POST /{id}/refund` and `PUT /{id}` to `AdminOrderController`
- Add `adminRefundOrder(UUID)` and `updateOrder(UUID, UpdateOrderRequest)` to `OrderService`
- Add `adminNotes` (TEXT), `shipping_address` (JSONB) columns to `checkout.orders` (V26 migration)

---

## 5. Order Fulfillment

### Data Model

```
Fulfillment
  ├─ id (UUIDv7)
  ├─ orderId          (FK → checkout.orders)
  ├─ status           (PENDING | SHIPPED | DELIVERED | FAILED)
  ├─ trackingNumber   (VARCHAR 128, nullable)
  ├─ trackingCompany  (VARCHAR 64, nullable)
  ├─ trackingUrl      (TEXT, nullable)
  ├─ note             (TEXT, nullable)
  ├─ createdAt, updatedAt
  └─< FulfillmentItem
        ├─ id (UUIDv7)
        ├─ fulfillmentId (FK → fulfillments)
        ├─ orderItemId   (FK → checkout.order_items)
        ├─ quantity      (INTEGER NOT NULL)
        ├─ createdAt, updatedAt
```

Schema: `checkout`. Tables: `fulfillments`, `fulfillment_items`.

**Order status transitions:**

```
PENDING_PAYMENT → PAID → PARTIALLY_FULFILLED → FULFILLED
                        ↘ CANCELLED
```

Add `PARTIALLY_FULFILLED` and `FULFILLED` to `OrderStatus` enum. The `FulfillmentService` recalculates order status after each fulfillment create/update:
- All ordered quantity fulfilled → `FULFILLED`
- Some but not all fulfilled → `PARTIALLY_FULFILLED`
- None yet fulfilled → `PAID`

### API Surface

```
GET    /api/v1/admin/orders/{id}/fulfillments                        (order:read)
POST   /api/v1/admin/orders/{id}/fulfillments                       (order:write)
GET    /api/v1/admin/orders/{id}/fulfillments/{fulfillmentId}       (order:read)
PUT    /api/v1/admin/orders/{id}/fulfillments/{fulfillmentId}       (order:write)
```

`POST` request body:

```json
{
  "trackingNumber": "1Z999...",
  "trackingCompany": "UPS",
  "trackingUrl": "https://...",
  "note": "Packed and shipped",
  "items": [
    { "orderItemId": "uuid", "quantity": 2 }
  ]
}
```

`PUT` updates tracking info and status only (items are immutable after creation).

### Package Structure

```
io.k2dv.garden.fulfillment
  ├─ model/        Fulfillment, FulfillmentItem, FulfillmentStatus
  ├─ repository/   FulfillmentRepository, FulfillmentItemRepository
  ├─ service/      FulfillmentService
  ├─ dto/          CreateFulfillmentRequest, UpdateFulfillmentRequest,
  │                FulfillmentResponse, FulfillmentItemRequest, FulfillmentItemResponse
  └─ controller/   AdminFulfillmentController
```

Emits `OrderEvent` of type `FULFILLMENT_CREATED` on POST (see Section 9).

---

## 6. File Manager List

### Design

`BlobController` already handles upload (POST) and delete (DELETE). Add a `GET` list endpoint and a `GET /{id}` single-item endpoint.

No new entities or services — `BlobService` gains `list(BlobFilter, Pageable)` and `getById(UUID)`.

### New Filter

```
BlobFilter
  ├─ contentType (nullable — e.g. "image/jpeg")
  └─ filenameContains (nullable)
```

### API Surface

```
GET /api/v1/admin/blobs              (blob:read)
    ?contentType=&filenameContains=&page=&size=

GET /api/v1/admin/blobs/{id}        (blob:read)
```

---

## 7. Customer Notes and Tags

### Design

Add two fields to the `User` entity (and `users` table):

```
User (additions)
  ├─ adminNotes  (TEXT, nullable — free-form notes visible only to admin)
  └─ tags        (TEXT[] — PostgreSQL array; empty by default)
```

Use PostgreSQL native `text[]` array for tags (no join table overhead for a simple string set). Hibernate maps `text[]` with `@Array` + `@Column(columnDefinition = "text[]")`.

**Convention note:** The review requests `PATCH` for these endpoints, but project convention prohibits `PATCH`. Use `PUT` with the full value of the field being replaced.

### API Surface

```
PUT /api/v1/admin/users/{id}/notes   (user:write)
    body: { "notes": "VIP customer. Handles North district." }

PUT /api/v1/admin/users/{id}/tags    (user:write)
    body: { "tags": ["vip", "wholesale", "north-district"] }
```

Both return the updated `AdminUserResponse`.

### Change

New Flyway migration adds two columns to `auth.users`. `AdminUserService` gets `updateNotes(UUID, String)` and `updateTags(UUID, List<String>)`. Two new endpoints on `AdminUserController`.

---

## 8. Gift Cards

### Package

`io.k2dv.garden.giftcard`

### Data Model

```
GiftCard
  ├─ id (UUIDv7)
  ├─ code              (VARCHAR 32, unique — auto-generated on create if not provided)
  ├─ initialBalance    (NUMERIC(19,4) NOT NULL)
  ├─ currentBalance    (NUMERIC(19,4) NOT NULL)
  ├─ currency          (VARCHAR 3 NOT NULL DEFAULT 'usd')
  ├─ isActive          (BOOLEAN NOT NULL DEFAULT TRUE)
  ├─ expiresAt         (TIMESTAMPTZ, nullable)
  ├─ note              (TEXT, nullable)
  ├─ purchaserUserId   (UUID, nullable FK → auth.users)
  ├─ recipientEmail    (VARCHAR 256, nullable)
  ├─ createdAt, updatedAt
  └─< GiftCardTransaction  (append-only ledger — extends ImmutableBaseEntity)
        ├─ id (UUIDv7)
        ├─ giftCardId  (FK → gift_cards)
        ├─ delta       (NUMERIC(19,4) NOT NULL — positive = credit, negative = debit)
        ├─ orderId     (UUID, nullable FK → checkout.orders)
        ├─ note        (TEXT, nullable)
        └─ createdAt
```

Schema: `checkout`. Tables: `gift_cards`, `gift_card_transactions`.

**Code generation:** if `code` not provided on create, generate a random 16-char alphanumeric code (uppercase). Stored as-is; lookups are case-insensitive (`ILIKE` or `LOWER()`).

**Balance invariants (enforced at service layer):**
- `currentBalance >= 0` always
- Debit rejected if `delta` would take balance negative
- `initialBalance > 0`

**`GiftCardTransaction` extends `ImmutableBaseEntity`** (no `updatedAt`). Mirrors `InventoryTransaction` pattern — append-only ledger.

### Gift Card Redemption at Checkout

A customer applies a gift card code at checkout alongside (or instead of) a Stripe payment. The flow:

1. Customer submits `giftCardCode` in the checkout request
2. `GiftCardService.redeem(String code, BigDecimal orderAmount)`:
   - Looks up gift card by code (case-insensitive)
   - Validates: `isActive = true`, not expired, `currentBalance > 0`
   - Calculates `appliedAmount = min(currentBalance, orderAmount)`
   - Atomically deducts: `UPDATE gift_cards SET current_balance = current_balance - :amount WHERE id = ? AND current_balance >= :amount` — 0 rows = concurrent depletion, throw `ConflictException("GIFT_CARD_INSUFFICIENT_BALANCE")`
   - Inserts a `GiftCardTransaction` with `delta = -appliedAmount` and `orderId` set after order creation
   - Returns `appliedAmount`
3. `CartService` / `QuoteService` subtracts `appliedAmount` from the Stripe charge amount
4. If `appliedAmount` covers the full order total, Stripe is not charged (total = 0); order is immediately set to PAID

**Combining gift card + discount:** Both can be applied. Order: apply discount first, then gift card covers remaining balance.

**Storefront validation endpoint:**
```
GET /api/v1/storefront/gift-cards/validate    (authenticated)
    ?code=GFTXYZ123
```
Returns `{ valid, currentBalance, currency }`. Does not deduct balance.

### API Surface

```
// Admin
GET    /api/v1/admin/gift-cards                        (gift_card:read)
POST   /api/v1/admin/gift-cards                        (gift_card:write)
GET    /api/v1/admin/gift-cards/{id}                   (gift_card:read)
PUT    /api/v1/admin/gift-cards/{id}                   (gift_card:write)
DELETE /api/v1/admin/gift-cards/{id}                   (gift_card:delete — deactivates, not hard-delete)

GET    /api/v1/admin/gift-cards/{id}/transactions      (gift_card:read)
POST   /api/v1/admin/gift-cards/{id}/transactions      (gift_card:write — manual credit/debit)

// Storefront
GET    /api/v1/storefront/gift-cards/validate          (authenticated)
       ?code=
```

**`DELETE` behaviour:** sets `isActive = false`. If `currentBalance > 0`, inserts a `GiftCardTransaction` with `delta = -currentBalance` and `note = "Deactivated by admin"` to preserve ledger integrity, then sets `currentBalance = 0`. Hard delete is not allowed because transactions reference the card. GET list filters: `?isActive=`, `?code=`, `?page=`, `?size=`.

### Package Structure

```
io.k2dv.garden.giftcard
  ├─ model/        GiftCard, GiftCardTransaction
  ├─ repository/   GiftCardRepository, GiftCardTransactionRepository
  ├─ service/      GiftCardService
  ├─ dto/          CreateGiftCardRequest, UpdateGiftCardRequest, GiftCardResponse,
  │                GiftCardFilter, GiftCardTransactionRequest, GiftCardTransactionResponse
  └─ controller/   AdminGiftCardController
```

---

## 9. Order Timeline / Notes (Events)

### Package

`io.k2dv.garden.order` (add to existing domain)

### Data Model

```
OrderEvent  (extends ImmutableBaseEntity — no updatedAt)
  ├─ id (UUIDv7)
  ├─ orderId      (FK → checkout.orders)
  ├─ type         (NOTE | STATUS_CHANGE | FULFILLMENT_CREATED | PAYMENT_CONFIRMED
                       | REFUND_ISSUED | CANCELLED)
  ├─ message      (TEXT, nullable — human-readable body; required for NOTE type)
  ├─ authorId     (UUID, nullable FK → auth.users — nullable: survives user deletion)
  ├─ authorName   (VARCHAR 128, nullable — denormalized snapshot)
  ├─ metadata     (JSONB, nullable — e.g. {"oldStatus":"PAID","newStatus":"FULFILLED"})
  └─ createdAt
```

Schema: `checkout`. Table: `order_events`.

**System-generated events:** `OrderService` and `FulfillmentService` emit events automatically:
- `PAYMENT_CONFIRMED` — on `confirmPayment()`
- `CANCELLED` — on `cancelOrder()`
- `REFUND_ISSUED` — on `refundOrder()` / `adminRefundOrder()`
- `STATUS_CHANGE` — when `OrderStatus` transitions (metadata includes old/new value)
- `FULFILLMENT_CREATED` — when a `Fulfillment` is created

**Manual NOTE events:** Admin POSTs a free-text note. `authorId` / `authorName` populated from `@CurrentUser`.

**Append-only:** Events are never updated or deleted.

### API Surface

```
GET  /api/v1/admin/orders/{id}/events          (order:read)
POST /api/v1/admin/orders/{id}/events          (order:write — NOTE type only)
```

GET returns chronological list (oldest first). No pagination — orders are not expected to have more than a few dozen events.

POST request body:
```json
{ "message": "Called customer to confirm delivery address." }
```

Response includes full `OrderEventResponse` list / single event.

### Package Structure (additions to `order` domain)

```
io.k2dv.garden.order
  ├─ model/        OrderEvent, OrderEventType   (new)
  ├─ repository/   OrderEventRepository         (new)
  ├─ service/      OrderEventService            (new — create + list; called by OrderService)
  └─ controller/   AdminOrderEventsController   (new)
```

---

## Flyway Migrations

| Version | File | Contents |
|---|---|---|
| V19 | `V19__seed_admin_permissions.sql` | New permissions + role assignments |
| V20 | `V20__create_discounts.sql` | `checkout.discounts`; ALTER TABLE orders ADD `discount_id`, `discount_amount` |
| V21 | `V21__create_shipping.sql` | `shipping` schema, `shipping_zones`, `shipping_rates` |
| V22 | `V22__create_fulfillments.sql` | `checkout.fulfillments`, `checkout.fulfillment_items` |
| V23 | `V23__create_order_events.sql` | `checkout.order_events` |
| V24 | `V24__create_gift_cards.sql` | `checkout.gift_cards`, `checkout.gift_card_transactions`; ALTER TABLE orders ADD `gift_card_id`, `gift_card_amount` |
| V25 | `V25__add_user_notes_tags.sql` | Add `admin_notes`, `tags` to `auth.users` |
| V26 | `V26__add_order_admin_fields.sql` | ALTER TABLE orders ADD `admin_notes` (TEXT), `shipping_address` (JSONB) |

**Note:** `OrderStatus` is a Java enum mapped with `@Enumerated(EnumType.STRING)` — no DB-level type change needed. Just add the new values to the Java enum and they serialize as strings.

---

---

## Cross-Cutting Notes

### API Conventions

- `PATCH` is not used; `PUT` replaces the targeted sub-resource fully
- All new admin endpoints require `@HasPermission` with the appropriate permission string
- All new list endpoints use offset pagination (`PagedResult<T>`)
- New storefront endpoints (`/api/v1/storefront/...`) use `@Authenticated`

### Testing

- Integration tests with Testcontainers PostgreSQL — standard `postgres:16` image, no extensions needed
- All tests `@Transactional` + `@Rollback`
- Unit tests for: discount validation logic, discount atomic `usedCount` increment, gift card balance invariants, gift card deactivation ledger entry, fulfillment status recalculation

### Order Event Emission

`OrderEventService` is injected into `OrderService` and `FulfillmentService`. To avoid circular dependency issues, inject via constructor and ensure `order` package does not depend on `fulfillment` package in the reverse direction. `FulfillmentService` depends on `OrderService` (to update order status) and `OrderEventService` (to emit events) — one-way dependency.
