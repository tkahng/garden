# Cart & Checkout Spec

**Status:** Current  
**Last updated:** 2026-05-26  
**Packages:** `io.k2dv.garden.cart`, `io.k2dv.garden.payment`, `io.k2dv.garden.discount`, `io.k2dv.garden.giftcard`, `io.k2dv.garden.shipping`

---

## Cart

### Cart
`checkout.carts` — `io.k2dv.garden.cart.model.Cart`

| Field | Constraint | Notes |
|---|---|---|
| userId | UUID, nullable | null for guest carts |
| sessionId | UUID, unique | guest identifier |
| status | CartStatus, not null | |
| companyId | UUID, nullable | B2B context |
| abandonedReminderSentAt | Instant, nullable | set by automation scheduler |

**CartStatus enum:** `ACTIVE` · `CHECKED_OUT` · `ABANDONED`

---

### CartItem
`checkout.cart_items`

| Field | Constraint |
|---|---|
| cartId | not null, FK |
| variantId | not null, FK |
| quantity | int, not null |
| unitPrice | NUMERIC(19,4), not null |

---

## API — Cart (`/api/v1/cart`, `@Authenticated`)

| Method | Path | Description |
|---|---|---|
| GET | `/` | Get or create active cart |
| DELETE | `/` | Clear all items |
| POST | `/items` | Add item; upserts if variant already present |
| PUT | `/items/{itemId}` | Update quantity |
| DELETE | `/items/{itemId}` | Remove item |
| PUT | `/company` | Set B2B company context (re-prices items) |
| DELETE | `/company` | Clear company context |
| POST | `/items/batch` | Bulk add items (CSV upload supported) |

## API — Guest Cart (`/api/v1/guest/cart`)

Same shape as cart; identified by `X-Guest-Session` header (UUID).

---

## CartService Logic

- `getOrCreateActiveCart(userId)` — lazy creates on first call
- `setCompanyContext(userId, companyId)` — validates membership, re-prices all items via `PriceListService.resolvePrice`
- `addItem` — validates: product ACTIVE, variant not deleted, inventory policy (DENY blocks if quantity < requested)
- On checkout: cart status → CHECKED_OUT

---

## Checkout / Payment

### ProcessedStripeEvent
`payment.processed_stripe_events` — idempotency record; prevents duplicate Stripe webhook processing.

---

## API — Checkout (`/api/v1/checkout`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/` | `@Authenticated` | Initiate checkout |
| POST | `/guest` | none (`X-Guest-Session`) | Guest checkout |
| GET | `/return?session_id=` | none | Verify payment return from Stripe |

**CheckoutRequest fields:** `discountCode`, `giftCardCode`, `shippingRateId`, `poNumber`

**CheckoutResponse:** `{ sessionId, checkoutUrl }`

---

## PaymentService Logic

**initiateCheckout:**
1. Load ACTIVE cart (throws if empty)
2. Apply discount code (if supplied) → validates active, dates, minOrder, maxUses
3. Apply gift card (if supplied) → validates active, balance
4. Resolve shipping rate → `ShippingService.calculateShipping(rateId, orderAmount)`
5. Calculate tax (if not tax-exempt company)
6. Create draft `Order`
7. `StripeGateway.createCheckoutSession()` → returns URL
8. Returns `checkoutUrl + sessionId`

**verifyReturn(sessionId):**
1. Lookup Order by stripeSessionId
2. Verify Stripe session status is `complete`
3. Order status → PAID
4. Publish `OrderConfirmedEvent` (triggers fulfillment, email)

**Stripe webhook handler:**
- Idempotent via `ProcessedStripeEvent` record
- Handles: `checkout.session.completed`, `payment_intent.payment_failed`

**PaymentReconciliationScheduler** (see `08-infra.md`): polls Stripe for stale PENDING_PAYMENT orders every 10 minutes.

---

## Discounts

### Discount
`checkout.discounts` — `io.k2dv.garden.discount.model.Discount`

| Field | Constraint | Notes |
|---|---|---|
| code | nullable | null for automatic discounts |
| automatic | boolean | if true, applied without code |
| type | DiscountType, not null | |
| value | NUMERIC, not null | |
| minOrderAmount | NUMERIC, nullable | |
| maxUses | int, nullable | null = unlimited |
| usedCount | int, default 0 | |
| startsAt, endsAt | Instant, nullable | |
| isActive | boolean | |
| companyId | UUID, nullable | B2B-only discount |

**DiscountType enum:** `PERCENTAGE` · `FIXED_AMOUNT` · `FREE_SHIPPING`

---

## API — Admin Discounts (`/api/v1/admin/discounts`)

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `discount:read` | List (filter: type, isActive, codeContains, companyId) |
| GET | `/{id}` | `discount:read` | Get |
| POST | `/` | `discount:write` | Create |
| PUT | `/{id}` | `discount:write` | Update |
| DELETE | `/{id}` | `discount:delete` | Delete |

**DiscountService.validateAndApply(code, orderAmount):** checks active, date range, minOrderAmount, maxUses. Returns `DiscountApplication { appliedAmount }`.

---

## Gift Cards

### GiftCard
`checkout.gift_cards`

| Field | Constraint | Notes |
|---|---|---|
| code | not null | redemption code |
| initialBalance | NUMERIC, not null | |
| currentBalance | NUMERIC, not null | decremented on redemption |
| currency | default "usd" | |
| isActive | boolean, default true | |
| expiresAt | Instant, nullable | |
| note | text, nullable | |
| purchaserUserId | UUID, nullable | |
| recipientEmail | nullable | |

### GiftCardTransaction
Immutable record per redemption — `giftCardId`, `orderId`, `amount`, `createdAt`.

---

## API — Gift Cards

*Storefront (`/api/v1/gift-cards`, `@Authenticated`):*

| Method | Path | Description |
|---|---|---|
| GET | `/balance?code=` | Check balance |
| POST | `/redeem` | Apply to cart/checkout |

*Admin (`/api/v1/admin/gift-cards`):*

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `gift_card:read` | List |
| POST | `/` | `gift_card:write` | Create/issue |
| PUT | `/{id}` | `gift_card:write` | Update (note, active, expiry) |
| DELETE | `/{id}` | `gift_card:delete` | Deactivate |

---

## Shipping

### ShippingZone
`shipping.shipping_zones` — `name`, `countries` (text array)

### ShippingRate
`shipping.shipping_rates`

| Field | Notes |
|---|---|
| zoneId | FK |
| name | not null |
| price | NUMERIC(19,4), not null |
| minWeightGrams, maxWeightGrams | nullable — weight-based filtering |
| minOrderAmount | nullable — threshold-based |
| estimatedDaysMin, estimatedDaysMax | nullable |
| carrier | nullable |
| isActive | boolean |

---

## API — Shipping

*Storefront (`/api/v1/shipping`, no auth):*

| Method | Path | Description |
|---|---|---|
| GET | `/rates?country=&weight=&orderAmount=` | List applicable rates |

*Admin (`/api/v1/admin/shipping`):*

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/zones` | `shipping:read` | List zones |
| POST | `/zones` | `shipping:write` | Create zone |
| PUT | `/zones/{id}` | `shipping:write` | Update zone |
| DELETE | `/zones/{id}` | `shipping:delete` | Delete zone |
| GET | `/zones/{id}/rates` | `shipping:read` | List rates |
| POST | `/zones/{id}/rates` | `shipping:write` | Create rate |
| PUT | `/rates/{id}` | `shipping:write` | Update rate |
| DELETE | `/rates/{id}` | `shipping:delete` | Delete rate |

**ShippingService.getAvailableRates(address, weightGrams, orderAmount):** filters zones by country match, rates by weight range + minOrderAmount, returns active rates.
