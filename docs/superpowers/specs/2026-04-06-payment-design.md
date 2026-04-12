# Payment — Cart, Order & Stripe Checkout Design Spec

**Date:** 2026-04-06
**Project:** `io.k2dv.garden`
**Phase:** 2

---

## Overview

This spec covers the Cart, Order, and Payment domains — the full pipeline from a customer adding items to their cart through to a confirmed Stripe payment.

**Key constraint:** No Stripe Product or Price object synchronization. All pricing is passed inline to Stripe via `price_data` on each Checkout Session line item. Our system is the source of truth for prices; Stripe only processes the payment.

---

## Architecture

### Package Structure

Three new packages are introduced:

| Package | Responsibility |
|---|---|
| `cart` | Cart sessions and line items for authenticated users |
| `order` | Order lifecycle — created from cart, tracks status through payment and fulfillment |
| `payment` | Stripe Checkout Session creation and webhook handling |

Domains communicate through service interfaces only:

```
CartService → OrderService → PaymentService (Stripe) → webhook → OrderService
```

`payment` calls Stripe and fires results back into `order`. `order` calls `inventory` to reserve/release/deduct stock. `cart` never talks to `payment` directly.

### Approach

Stripe Checkout Sessions with `price_data` inline. The customer is redirected to Stripe's hosted checkout page. Confirmation uses both:
- A webhook (`checkout.session.completed`) as the authoritative source
- A redirect return URL for optimistic status display in the frontend

---

## Data Model

### Cart

```
Cart
  ├─ id (UUIDv7)
  ├─ userId (FK → User, unique partial index WHERE status = 'ACTIVE')
  ├─ status: ACTIVE | CHECKED_OUT | ABANDONED
  ├─ createdAt, updatedAt
  └─< CartItem
        ├─ id (UUIDv7)
        ├─ cartId (FK → Cart)
        ├─ variantId (FK → ProductVariant)
        ├─ quantity (int, min 1)
        ├─ unitPrice (BigDecimal — snapshot of variant.price at time of add)
        └─ createdAt, updatedAt
```

**One active cart per user** — enforced by a unique partial index: `CREATE UNIQUE INDEX ON checkout.carts (user_id) WHERE status = 'ACTIVE'`.

`unitPrice` is snapshotted when the item is added. Subsequent price changes on the variant do not affect existing cart items.

---

### Order

```
Order
  ├─ id (UUIDv7)
  ├─ userId (FK → User)
  ├─ status: PENDING_PAYMENT | PAID | CANCELLED | REFUNDED
  ├─ stripeSessionId (nullable — set when Stripe Checkout Session is created)
  ├─ stripePaymentIntentId (nullable — set when webhook confirms payment)
  ├─ totalAmount (BigDecimal — sum of line items at order creation time)
  ├─ currency (String, e.g. "usd")
  ├─ createdAt, updatedAt
  └─< OrderItem
        ├─ id (UUIDv7)
        ├─ orderId (FK → Order)
        ├─ variantId (FK → ProductVariant)
        ├─ quantity (int)
        ├─ unitPrice (BigDecimal — copied from CartItem snapshot)
        └─ createdAt, updatedAt
```

---

### Inventory Additions

`InventoryLevel` gains a `quantity_reserved` column (anticipated in the Phase 2 roadmap). Added via a new Flyway migration.

```
available = quantity_on_hand - quantity_reserved
```

---

## API Surface

### Cart

All cart endpoints require `@Authenticated`.

```
GET    /api/v1/cart                    — get current user's active cart (creates one if none exists)
DELETE /api/v1/cart                    — abandon current cart

POST   /api/v1/cart/items              — add item { variantId, quantity }
PUT    /api/v1/cart/items/{itemId}     — update quantity { quantity }
DELETE /api/v1/cart/items/{itemId}     — remove item
```

### Checkout

```
POST   /api/v1/checkout                — create Stripe Checkout Session from active cart
                                         returns { checkoutUrl, orderId }

GET    /api/v1/checkout/return         — Stripe redirects here after payment (success or cancel)
                                         reads ?session_id=, verifies with Stripe, returns order status
```

Both endpoints require `@Authenticated`.

### Webhook

```
POST   /api/v1/webhooks/stripe         — Stripe event delivery (public, Stripe-signature verified)
```

No authentication — Stripe calls this directly. Verified via `Stripe-Signature` header using the webhook signing secret.

### Storefront Orders

All endpoints require `@Authenticated`. Users can only access their own orders.

```
GET    /api/v1/storefront/orders               — list the current user's orders (paged, newest first)
GET    /api/v1/storefront/orders/{id}          — order detail with line items (ownership enforced)
PUT    /api/v1/storefront/orders/{id}/cancel   — cancel a PENDING_PAYMENT order owned by the user
POST   /api/v1/storefront/orders/{id}/refund   — request a refund on a PAID order owned by the user
```

**Cancel rules:** Only `PENDING_PAYMENT` orders can be cancelled. Releases inventory reservation.

**Refund rules:** Only `PAID` orders can be refunded. Issues a full refund via Stripe's Refund API using the stored `stripePaymentIntentId`. Transitions order to `REFUNDED`. Inventory is **not** restocked automatically (admin handles that).

### Admin Orders

```
GET    /api/v1/admin/orders                    — list orders (filter by status, userId, date range)  (order:read)
GET    /api/v1/admin/orders/{id}               — order detail with line items  (order:read)
PUT    /api/v1/admin/orders/{id}/cancel        — cancel a PENDING_PAYMENT order  (order:write)
```

---

## Checkout Flow

### Step 1 — Initiate Checkout (`POST /api/v1/checkout`)

1. Load the user's `ACTIVE` cart; error if empty
2. For each `CartItem`, validate:
   - Variant exists and is not soft-deleted
   - Parent product is `ACTIVE`
   - Available stock (`onHand - reserved`) >= requested quantity
3. Create `Order` with status `PENDING_PAYMENT`; copy cart items to `OrderItem`s
4. Reserve inventory: increment `quantity_reserved` on each `InventoryLevel` for each item
5. Build Stripe Checkout Session:
   - `line_items` use `price_data` (no Stripe Price ID). `unit_amount` is in the smallest currency unit (cents): `unitPrice.multiply(100).longValue()`.
     ```json
     {
       "price_data": {
         "currency": "usd",
         "unit_amount": 4999,
         "product_data": { "name": "Variant Title" }
       },
       "quantity": 2
     }
     ```
   - `mode: "payment"`
   - `success_url`: `{app.base-url}/checkout/return?session_id={CHECKOUT_SESSION_ID}`
   - `cancel_url`: `{app.base-url}/checkout/return?session_id={CHECKOUT_SESSION_ID}`
   - `metadata`: `{ "orderId": "<uuid>" }` — used by webhook to look up the order
6. Set `Order.stripeSessionId`; transition `Cart` to `CHECKED_OUT`
7. Return `{ checkoutUrl, orderId }`

### Step 2 — Stripe Webhook (`POST /api/v1/webhooks/stripe`)

**`checkout.session.completed`:**
1. Verify `Stripe-Signature` header using webhook signing secret
2. Look up `Order` by `stripeSessionId` (from `session.id`)
3. Guard: if order is already `PAID`, ignore (idempotent)
4. Set `Order.stripePaymentIntentId` from `session.payment_intent`
5. Transition `Order`: `PENDING_PAYMENT` → `PAID`
6. For each `OrderItem`: decrement `quantity_on_hand`, decrement `quantity_reserved`, insert `InventoryTransaction` (reason: `SOLD`)

**`checkout.session.expired`:**
1. Verify signature
2. Look up `Order` by `stripeSessionId`
3. Guard: if order is already `CANCELLED`, ignore
4. Transition `Order`: `PENDING_PAYMENT` → `CANCELLED`
5. Release reservation: decrement `quantity_reserved` for each item

### Step 3 — Return Redirect (`GET /api/v1/checkout/return`)

1. Read `?session_id=` query param
2. Call Stripe API to fetch the session status
3. Return `{ orderId, status }` — frontend uses this for optimistic display
4. Webhook is the authoritative confirmation; this is best-effort only

---

## Order Status Transitions

```
PENDING_PAYMENT ──(webhook: completed)────────► PAID
PENDING_PAYMENT ──(webhook: expired)──────────► CANCELLED
PENDING_PAYMENT ──(admin cancel)──────────────► CANCELLED
PENDING_PAYMENT ──(user cancel)───────────────► CANCELLED
PAID ────────────(user refund / admin refund)──► REFUNDED
```

Invalid transitions (e.g. cancelling a `PAID` order) are rejected with `ConflictException`.

---

## Stripe Configuration

Config properties (added to `application.properties` / environment):

```
stripe.secret-key=sk_test_...
stripe.webhook-secret=whsec_...
app.base-url=https://yourapp.com
```

`stripe.secret-key` is used for all Stripe API calls. `stripe.webhook-secret` is used to verify the `Stripe-Signature` header on incoming webhook events.

---

## Database Schema

New Flyway migration (V14):

- Schema: `checkout` (new)
- Tables: `checkout.carts`, `checkout.cart_items`, `checkout.orders`, `checkout.order_items`
- Unique partial index on `checkout.carts(user_id) WHERE status = 'ACTIVE'`
- `inventory.inventory_levels` gains `quantity_reserved INT NOT NULL DEFAULT 0`

---

## Permission Matrix Addition

| Resource | Actions |
|---|---|
| `order` | `read`, `write` |

---

## Testing Strategy

- **CartService, OrderService integration tests** — Testcontainers PostgreSQL, `@Transactional` + `@Rollback`
- **PaymentService** — unit-tested with a mocked Stripe SDK client; no real Stripe calls in CI
- **Webhook handler** — unit-tested with a mocked `PaymentService`; signature verification tested with a known test secret
- **Controller slice tests** — `@WebMvcTest` for cart, checkout, and admin order controllers

---

## Summary

| Concern | Decision |
|---|---|
| Stripe integration | Checkout Sessions with `price_data` — no Product/Price sync |
| Cart scope | Authenticated users only; one active cart per user |
| Guest checkout | Not in scope; future consideration |
| Payment confirmation | Webhook (authoritative) + redirect verify (optimistic) |
| Inventory | Reserve on order creation; deduct on payment confirmed; release on cancellation |
| Webhook security | `Stripe-Signature` header verification via signing secret |
| Order status source of truth | Webhook events |
