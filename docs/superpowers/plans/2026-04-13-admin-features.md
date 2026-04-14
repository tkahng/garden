# Admin Features Implementation Plan

**Date:** 2026-04-13
**Project:** `io.k2dv.garden`
**Branch:** `feat/admin`
**Spec:** `docs/superpowers/specs/2026-04-13-admin-features-design.md`

---

## Sequencing Rationale

- **Infrastructure first:** PostGIS extension + new permissions unblock everything else
- **Low-dependency features next:** Stats, Discounts, File manager list, Customer notes/tags — no new cross-domain wiring
- **Order-adjacent features grouped:** Admin refunds, Fulfillment, Order events — share `OrderService` and `OrderStatus` enum changes; implement together to avoid multiple passes
- **Gift cards last:** Self-contained but non-trivial; no blockers after permissions migration

---

## Phase 1 — Infrastructure & Migrations

### Step 1.1 — Permissions Migration

- `V19__seed_admin_permissions.sql`
- Insert new permissions: `order:read`, `order:write`, `discount:read/write/delete`, `shipping:read/write/delete`, `gift_card:read/write/delete`, `stats:read`, `blob:read`
- Assign to roles: MANAGER gets order/discount/shipping/gift_card; STAFF gets stats:read + blob:read; OWNER gets all (via existing catch-all insert)

---

## Phase 2 — Low-Dependency Features

### Step 2.1 — Analytics / Dashboard Stats

**Migration:** none

**New files:**
- `stats/dto/StatsResponse.java` (record)
- `stats/service/StatsService.java` — JPQL aggregation queries over `Order` + `User`
- `stats/controller/AdminStatsController.java`

**Endpoint:** `GET /api/v1/admin/stats?from=&to=`

**Test:** `AdminStatsControllerTest` — create orders in known states, assert revenue/count/AOV values

---

### Step 2.2 — Discounts

**Migration:** `V20__create_discounts.sql`

```sql
CREATE TABLE checkout.discounts (
    id          UUID PRIMARY KEY,
    code        VARCHAR(64) NOT NULL,
    type        VARCHAR(32) NOT NULL,
    value       NUMERIC(19,4) NOT NULL DEFAULT 0,
    min_order_amount NUMERIC(19,4),
    max_uses    INTEGER,
    used_count  INTEGER NOT NULL DEFAULT 0,
    starts_at   TIMESTAMPTZ,
    ends_at     TIMESTAMPTZ,
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX discounts_code_uq ON checkout.discounts (UPPER(code));
CREATE TRIGGER discounts_updated_at BEFORE UPDATE ON checkout.discounts
    FOR EACH ROW EXECUTE FUNCTION util.set_updated_at();
```

**Migration also adds to orders table:**
```sql
ALTER TABLE checkout.orders
    ADD COLUMN discount_id     UUID REFERENCES checkout.discounts(id),
    ADD COLUMN discount_amount NUMERIC(19,4);
```

**New files:**
- `discount/model/Discount.java`, `DiscountType.java`
- `discount/repository/DiscountRepository.java`
- `discount/service/DiscountService.java`
  - `redeem(String code, BigDecimal orderAmount)` — validates + atomic `UPDATE used_count = used_count + 1 WHERE used_count < max_uses`; returns `DiscountApplication`
  - `validate(String code, BigDecimal orderAmount)` — read-only check, no increment
  - standard CRUD methods
- `discount/dto/CreateDiscountRequest.java`, `UpdateDiscountRequest.java`, `DiscountResponse.java`, `DiscountFilter.java`, `DiscountApplication.java`, `DiscountValidationResponse.java`
- `discount/specification/DiscountSpecification.java`
- `discount/controller/AdminDiscountController.java`
- `discount/controller/StorefrontDiscountController.java` — `GET /api/v1/storefront/discounts/validate`

**Wiring:** `CartService` accepts optional `discountCode` in checkout request; calls `DiscountService.redeem()` before creating the `Order`; stores `discountId` + `discountAmount` on the order.

**Test:** `AdminDiscountControllerTest` — CRUD + code uniqueness + validation rules
`DiscountServiceTest` (unit) — concurrent redeem exhausts at exact `maxUses`; expired discount rejected; min order amount enforced

---

### Step 2.3 — File Manager List

**Migration:** none

**Changes to existing files:**
- `blob/dto/BlobFilter.java` (new record: `contentType`, `filenameContains`)
- `blob/service/BlobService.java` — add `list(BlobFilter, Pageable)` and `getById(UUID)`
- `blob/controller/BlobController.java` — add `GET /` and `GET /{id}` endpoints with `blob:read`

**Test:** extend existing `BlobControllerTest`

---

### Step 2.4 — Customer Notes and Tags

**Migration:** `V25__add_user_notes_tags.sql`

```sql
ALTER TABLE auth.users
    ADD COLUMN admin_notes TEXT,
    ADD COLUMN tags        TEXT[] NOT NULL DEFAULT '{}';
```

**Changes to existing files:**
- `user/model/User.java` — add `adminNotes` (String), `tags` (List<String> mapped with `@Array`)
- `admin/user/dto/AdminUserResponse.java` — include `adminNotes` and `tags`
- `admin/user/service/AdminUserService.java` — add `updateNotes(UUID, String)` and `updateTags(UUID, List<String>)`
- `admin/user/controller/AdminUserController.java` — add two PUT endpoints

**Test:** `AdminUserControllerTest` — update notes, update tags, verify response

---

## Phase 3 — Order-Adjacent Features

> Implement steps 3.1–3.3 together; they share `OrderStatus` enum changes and `OrderEventService`.

### Step 3.1 — Order Status Enum + Event Infrastructure

**Migration:** `V23__create_order_events.sql`

```sql
CREATE TABLE checkout.order_events (
    id          UUID PRIMARY KEY,
    order_id    UUID NOT NULL REFERENCES checkout.orders(id),
    type        VARCHAR(64) NOT NULL,
    message     TEXT,
    author_id   UUID,
    author_name VARCHAR(128),
    metadata    JSONB,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX order_events_order_id_idx ON checkout.order_events (order_id, created_at);
```

**New/changed files:**
- `order/model/OrderStatus.java` — add `PARTIALLY_FULFILLED`, `FULFILLED`
- `order/model/OrderEvent.java`, `OrderEventType.java`
- `order/repository/OrderEventRepository.java`
- `order/service/OrderEventService.java` — `emit(UUID orderId, OrderEventType, String message, UUID authorId, String authorName, Map metadata)` + `list(UUID orderId)`
- `order/dto/OrderEventResponse.java`, `CreateOrderNoteRequest.java`
- `order/controller/AdminOrderEventsController.java`

`OrderService` gets `OrderEventService` injected; emit events in `confirmPayment()`, `cancelOrder()`, `refundOrder()`.

---

### Step 3.2 — Admin-Side Refunds and Order Update

**Migration:** `V26__add_order_admin_fields.sql`

```sql
ALTER TABLE checkout.orders
    ADD COLUMN admin_notes      TEXT,
    ADD COLUMN shipping_address JSONB;
```

**Changes to existing files:**
- `order/model/Order.java` — add `adminNotes`, `shippingAddress` (String JSON)
- `order/dto/OrderResponse.java` — include new fields
- `order/dto/UpdateOrderRequest.java` (new record)
- `order/service/OrderService.java` — add `adminRefundOrder(UUID)` and `updateOrder(UUID, UpdateOrderRequest)`
- `order/controller/AdminOrderController.java` — add `POST /{id}/refund` and `PUT /{id}` with `order:write`

**Test:** `AdminOrderControllerTest` — refund PAID order, assert REFUNDED + event emitted; update admin notes + shipping address, assert response reflects changes

---

### Step 3.3 — Order Fulfillment

**Migration:** `V22__create_fulfillments.sql`

```sql
CREATE TABLE checkout.fulfillments (
    id               UUID PRIMARY KEY,
    order_id         UUID NOT NULL REFERENCES checkout.orders(id),
    status           VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    tracking_number  VARCHAR(128),
    tracking_company VARCHAR(64),
    tracking_url     TEXT,
    note             TEXT,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TABLE checkout.fulfillment_items (
    id               UUID PRIMARY KEY,
    fulfillment_id   UUID NOT NULL REFERENCES checkout.fulfillments(id),
    order_item_id    UUID NOT NULL REFERENCES checkout.order_items(id),
    quantity         INTEGER NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER fulfillments_updated_at BEFORE UPDATE ON checkout.fulfillments
    FOR EACH ROW EXECUTE FUNCTION util.set_updated_at();
CREATE TRIGGER fulfillment_items_updated_at BEFORE UPDATE ON checkout.fulfillment_items
    FOR EACH ROW EXECUTE FUNCTION util.set_updated_at();
```

**New files:**
- `fulfillment/model/Fulfillment.java`, `FulfillmentItem.java`, `FulfillmentStatus.java`
- `fulfillment/repository/FulfillmentRepository.java`, `FulfillmentItemRepository.java`
- `fulfillment/service/FulfillmentService.java`
  - `create(UUID orderId, CreateFulfillmentRequest, User admin)` — creates fulfillment + items, recalculates order status, emits `FULFILLMENT_CREATED` event
  - `update(UUID orderId, UUID fulfillmentId, UpdateFulfillmentRequest)` — tracking + status only
  - `list(UUID orderId)` / `getById(UUID orderId, UUID fulfillmentId)`
- `fulfillment/dto/` — request/response DTOs
- `fulfillment/controller/AdminFulfillmentController.java` — nested under `/api/v1/admin/orders/{id}/fulfillments`

**Order status recalculation logic (in `FulfillmentService`):**
1. Sum `fulfillment_items.quantity` per `order_item_id` for PENDING/SHIPPED/DELIVERED fulfillments
2. Compare to `order_items.quantity` for each item
3. All fulfilled → `FULFILLED`; any fulfilled → `PARTIALLY_FULFILLED`; none → `PAID`

**Test:** `AdminFulfillmentControllerTest` — create fulfillment, assert order status change, assert event emitted

---

## Phase 4 — Shipping Zones

### Step 4.1 — Shipping Zones and Rates

**Migration:** `V21__create_shipping.sql`

```sql
CREATE SCHEMA shipping;

CREATE TABLE shipping.shipping_zones (
    id            UUID PRIMARY KEY,
    name          VARCHAR(128) NOT NULL,
    description   TEXT,
    country_codes TEXT[],
    provinces     TEXT[],
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER shipping_zones_updated_at BEFORE UPDATE ON shipping.shipping_zones
    FOR EACH ROW EXECUTE FUNCTION util.set_updated_at();

CREATE TABLE shipping.shipping_rates (
    id                 UUID PRIMARY KEY,
    zone_id            UUID NOT NULL REFERENCES shipping.shipping_zones(id),
    name               VARCHAR(128) NOT NULL,
    price              NUMERIC(19,4) NOT NULL,
    min_weight_grams   INTEGER,
    max_weight_grams   INTEGER,
    min_order_amount   NUMERIC(19,4),
    estimated_days_min INTEGER,
    estimated_days_max INTEGER,
    carrier            VARCHAR(64),
    is_active          BOOLEAN NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at         TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE TRIGGER shipping_rates_updated_at BEFORE UPDATE ON shipping.shipping_rates
    FOR EACH ROW EXECUTE FUNCTION util.set_updated_at();
```

**New files:**
- `shipping/model/ShippingZone.java`, `ShippingRate.java`
  - `countryCodes` and `provinces` mapped as `List<String>` with `@Array` + `@Column(columnDefinition = "text[]")`
- `shipping/repository/ShippingZoneRepository.java` — native query for country/province array matching
- `shipping/repository/ShippingRateRepository.java`
- `shipping/service/ShippingService.java`
  - `findRatesForAddress(String country, String province, BigDecimal orderAmount)` — filters zones by array membership, returns matching rates sorted by price
- `shipping/dto/` — request/response DTOs
- `shipping/controller/AdminShippingController.java`
- `shipping/controller/StorefrontShippingController.java`

**Zone lookup query (native SQL):**

```sql
SELECT sz.*
FROM shipping.shipping_zones sz
WHERE sz.is_active = true
  AND (sz.country_codes IS NULL OR :country = ANY(sz.country_codes))
  AND (sz.provinces IS NULL OR :province = ANY(sz.provinces) OR :province IS NULL)
```

**Test:** `AdminShippingControllerTest` — CRUD zones and rates
`StorefrontShippingControllerTest` — create zones with different country/province scopes, assert correct rates returned for a given address

---

## Phase 5 — Gift Cards

### Step 5.1 — Gift Cards

**Migration:** `V24__create_gift_cards.sql`

```sql
CREATE TABLE checkout.gift_cards (
    id                UUID PRIMARY KEY,
    code              VARCHAR(32) NOT NULL,
    initial_balance   NUMERIC(19,4) NOT NULL,
    current_balance   NUMERIC(19,4) NOT NULL,
    currency          VARCHAR(3) NOT NULL DEFAULT 'usd',
    is_active         BOOLEAN NOT NULL DEFAULT TRUE,
    expires_at        TIMESTAMPTZ,
    note              TEXT,
    purchaser_user_id UUID,
    recipient_email   VARCHAR(256),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE UNIQUE INDEX gift_cards_code_uq ON checkout.gift_cards (LOWER(code));

CREATE TABLE checkout.gift_card_transactions (
    id           UUID PRIMARY KEY,
    gift_card_id UUID NOT NULL REFERENCES checkout.gift_cards(id),
    delta        NUMERIC(19,4) NOT NULL,
    order_id     UUID,
    note         TEXT,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);
CREATE INDEX gct_gift_card_id_idx ON checkout.gift_card_transactions (gift_card_id, created_at);

CREATE TRIGGER gift_cards_updated_at BEFORE UPDATE ON checkout.gift_cards
    FOR EACH ROW EXECUTE FUNCTION util.set_updated_at();
```

**Migration also adds to orders table:**
```sql
ALTER TABLE checkout.orders
    ADD COLUMN gift_card_id     UUID REFERENCES checkout.gift_cards(id),
    ADD COLUMN gift_card_amount NUMERIC(19,4);
```

**New files:**
- `giftcard/model/GiftCard.java`, `GiftCardTransaction.java`
- `giftcard/repository/GiftCardRepository.java`, `GiftCardTransactionRepository.java`
- `giftcard/service/GiftCardService.java`
  - `create(CreateGiftCardRequest)` — auto-generates code if absent
  - `update(UUID, UpdateGiftCardRequest)`
  - `deactivate(UUID)` — if `currentBalance > 0`, inserts write-off `GiftCardTransaction(delta = -currentBalance, note = "Deactivated by admin")` first; then sets `isActive=false`, `currentBalance=0`
  - `redeem(String code, BigDecimal orderAmount)` — validates + atomic `UPDATE current_balance = current_balance - :amount WHERE current_balance >= :amount`; inserts debit transaction; returns applied amount
  - `validate(String code)` — read-only check; returns balance info
  - `addTransaction(UUID, GiftCardTransactionRequest)` — manual admin credit/debit
  - `list(GiftCardFilter, Pageable)` / `getById(UUID)` / `listTransactions(UUID)`
- `giftcard/dto/` — request/response DTOs; add `GiftCardValidationResponse`
- `giftcard/controller/AdminGiftCardController.java`
- `giftcard/controller/StorefrontGiftCardController.java` — `GET /api/v1/storefront/gift-cards/validate`

**Wiring:** `CartService` accepts optional `giftCardCode`; calls `GiftCardService.redeem()` after discount is applied; reduces Stripe charge by `appliedAmount`; stores `giftCardId` + `giftCardAmount` on the order. If remaining charge = 0, skips Stripe entirely and sets order to PAID directly.

**Test:** `AdminGiftCardControllerTest` — create, update, deactivate (assert write-off transaction), credit/debit, balance floor invariant
`GiftCardServiceTest` (unit) — deactivation with non-zero balance emits ledger entry; concurrent redeem doesn't overdraw

---

## Summary Table

| Phase | Step | Feature | Migrations | New Package |
|---|---|---|---|---|
| 1 | 1.1 | Permissions | V19 | — |
| 2 | 2.1 | Analytics stats | — | `stats` |
| 2 | 2.2 | Discounts + application + storefront validate | V20 (+ ALTER orders) | `discount` |
| 2 | 2.3 | File manager list | — | — (blob) |
| 2 | 2.4 | Customer notes/tags | V25 | — (user) |
| 3 | 3.1 | Order events infra | V23 | order additions |
| 3 | 3.2 | Admin refunds + order update | V26 | — (order) |
| 3 | 3.3 | Fulfillment | V22 | `fulfillment` |
| 4 | 4.1 | Shipping zones/rates | V21 | `shipping` |
| 5 | 5.1 | Gift cards + redemption + storefront validate | V24 (+ ALTER orders) | `giftcard` |
