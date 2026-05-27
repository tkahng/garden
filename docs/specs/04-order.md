# Order & Fulfillment Spec

**Status:** Current  
**Last updated:** 2026-05-26  
**Packages:** `io.k2dv.garden.order`, `io.k2dv.garden.fulfillment`

---

## Order

### Order
`checkout.orders` — `io.k2dv.garden.order.model.Order`

| Field | Constraint | Notes |
|---|---|---|
| userId | UUID, nullable | null for guest orders |
| guestEmail | nullable | set for guest orders |
| status | OrderStatus, not null | |
| shippingAddress | jsonb, nullable | embedded delivery address |
| shippingRateId | UUID, nullable | |
| shippingCost | NUMERIC(19,4) | |
| totalAmount | NUMERIC(19,4), not null | |
| currency | String, default "usd" | |
| taxAmount | NUMERIC(19,4) | |
| taxExempt | boolean | |
| discountId | UUID, nullable | |
| discountAmount | NUMERIC(19,4) | |
| giftCardId | UUID, nullable | |
| giftCardAmount | NUMERIC(19,4) | |
| stripeSessionId | nullable | |
| stripePaymentIntentId | nullable | |
| poNumber | nullable | B2B purchase order number |
| companyId | UUID, nullable | B2B company |
| adminNotes | text, nullable | |
| lockVersion | int | optimistic lock |

**OrderStatus enum:**  
`DRAFT` · `PENDING_PAYMENT` · `PENDING_APPROVAL` · `PAID` · `CANCELLED` · `REFUNDED` · `PARTIALLY_FULFILLED` · `FULFILLED` · `INVOICED`

---

### OrderItem
`checkout.order_items`

| Field | Constraint |
|---|---|
| orderId | not null, FK |
| variantId | not null, FK |
| quantity | int, not null |
| unitPrice | NUMERIC(19,4), not null |

---

### OrderEvent
`checkout.order_events` — immutable audit trail per order.

| Field | Notes |
|---|---|
| orderId | FK |
| type | OrderEventType enum |
| note | text, nullable |
| metadata | jsonb, nullable |
| createdBy | UUID, nullable |

**OrderEventType enum:**  
`ORDER_PLACED` · `PAYMENT_CONFIRMED` · `ORDER_CANCELLED` · `ORDER_REFUNDED` · `ADMIN_REFUND_ISSUED` · `DISCOUNT_APPLIED` · `GIFT_CARD_APPLIED` · `FULFILLMENT_CREATED` · `FULFILLMENT_UPDATED` · `NOTE_ADDED` · `INVOICE_ISSUED` · `ORDER_APPROVAL_REQUESTED` · `ORDER_APPROVAL_APPROVED` · `ORDER_APPROVAL_REJECTED` · `RETURN_REQUESTED` · `RETURN_APPROVED` · `RETURN_REJECTED` · `RETURN_COMPLETED`

---

### ReturnRequest
`checkout.return_requests`

| Field | Constraint | Notes |
|---|---|---|
| orderId | not null, FK | |
| userId | not null | |
| status | ReturnRequestStatus, not null | |
| reason | ReturnReason, not null | |
| resolution | ReturnResolution, nullable | set on approval |
| adminNotes | text, nullable | |

**ReturnRequestStatus enum:** `PENDING` · `APPROVED` · `REJECTED` · `COMPLETED`  
**ReturnReason enum:** `DEFECTIVE` · `WRONG_ITEM` · `NOT_AS_DESCRIBED` · `CHANGED_MIND` · `OTHER`  
**ReturnResolution enum:** `REFUND` · `REPLACEMENT` · `STORE_CREDIT`

---

### OrderTemplate
`checkout.order_templates` — saved reusable order configurations per user.

---

## API — Storefront Orders (`/api/v1/storefront/orders`, `@Authenticated`)

| Method | Path | Description |
|---|---|---|
| GET | `/` | List caller's orders (paginated) |
| GET | `/{id}` | Get order (ownership verified) |
| PUT | `/{id}/cancel` | Cancel order (allowed in PENDING_PAYMENT) |
| POST | `/{id}/returns` | Submit return request |
| GET | `/{id}/returns` | List return requests for order |
| GET | `/{id}/fulfillments` | List fulfillments |

---

## API — Admin Orders (`/api/v1/admin/orders`)

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `order:read` | List orders (filter: status, userId, companyId, date range) |
| GET | `/{id}` | `order:read` | Get order with items + events |
| PUT | `/{id}` | `order:write` | Update order (adminNotes, poNumber) |
| POST | `/{id}/cancel` | `order:write` | Cancel order |
| POST | `/{id}/refund` | `order:write` | Issue refund via Stripe |
| POST | `/{id}/events` | `order:write` | Add note event |
| GET | `/{id}/events` | `order:read` | List order events |

---

## API — Admin Returns (`/api/v1/admin/returns`)

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `order:read` | List return requests (filterable) |
| GET | `/{id}` | `order:read` | Get return request |
| POST | `/{id}/approve` | `order:write` | Approve return (set resolution) |
| POST | `/{id}/reject` | `order:write` | Reject return |
| POST | `/{id}/complete` | `order:write` | Mark complete (return received) |

---

## OrderService Logic

- **createFromCart:** validates inventory, applies discount + gift card + tax + shipping; creates Order in PENDING_PAYMENT; reserves inventory; clears cart
- **confirmPayment:** Stripe webhook or return verification → PAID; records payment event; publishes `OrderConfirmedEvent`; runs `AutoTagService.applyOrderTags`
- **cancel:** allowed if PENDING_PAYMENT or PAID (pre-fulfillment); releases inventory; publishes `OrderCancelledEvent`
- **refund:** calls Stripe refund API; status → REFUNDED; records event
- Optimistic locking via `lockVersion` on Order

---

## Fulfillment

### Fulfillment
`checkout.fulfillments`

| Field | Constraint | Notes |
|---|---|---|
| orderId | not null, FK | |
| status | FulfillmentStatus, not null | |
| trackingNumber | nullable | |
| trackingCompany | nullable | |
| trackingUrl | nullable | |
| note | text, nullable | |

**FulfillmentStatus enum:** `PENDING` · `SHIPPED` · `DELIVERED` · `CANCELLED`

---

### FulfillmentItem
`checkout.fulfillment_items`

- `fulfillmentId`, `orderItemId`, `quantity`

---

## API — Admin Fulfillments (`/api/v1/admin/fulfillments`)

| Method | Path | Permission | Description |
|---|---|---|---|
| POST | `/orders/{orderId}/fulfillments` | `fulfillment:write` | Create fulfillment |
| PUT | `/{id}` | `fulfillment:write` | Update tracking info / status |
| DELETE | `/{id}` | `fulfillment:delete` | Cancel fulfillment (returns inventory) |

---

## FulfillmentService Logic

- **create:** validates order not PENDING_PAYMENT/CANCELLED/REFUNDED; validates items belong to order; prevents over-fulfillment (quantity check against already-fulfilled qty); decrements `quantityOnHand`; transitions Order to PARTIALLY_FULFILLED or FULFILLED
- **update:** updates tracking fields; SHIPPED triggers notification email and `FULFILLMENT_SHIPPED` webhook; DELIVERED triggers `FULFILLMENT_DELIVERED` webhook
- **delete/cancel:** returns inventory (`quantityOnHand += qty`); recalculates order fulfillment status

---

## Order Templates (`/api/v1/storefront/order-templates`, `@Authenticated`)

| Method | Path | Description |
|---|---|---|
| GET | `/` | List templates |
| POST | `/` | Save current cart as template |
| GET | `/{id}` | Get template |
| POST | `/{id}/apply` | Load template items into active cart |
| DELETE | `/{id}` | Delete template |
