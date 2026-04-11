# Quote System Design Spec

**Date:** 2026-04-10
**Project:** `io.k2dv.garden`
**Branch:** `feat/storefront-support`

---

## Overview

This spec covers a B2B Quote system — the full pipeline from a customer building a quote cart through to a fulfilled quote PDF, user acceptance, and Stripe payment.

**Key constraints:**
- Quote-only products (variants with null price) can only be added to the quote cart, not the regular cart.
- Quote requests are tied to a Company entity. Users must belong to a company to submit a quote request.
- Regular cart → checkout flow is unaffected.
- Once a staff member sends a quote, the user can accept it in-app, which creates a real Order and routes to Stripe.
- PDF generation uses OpenHTMLtoPDF (styled HTML → PDF).

---

## Architecture

### Approach

Two new packages: `b2b` and `quote`. When a user accepts a quote, `QuoteService` calls `OrderService.createFromQuote()` to produce a real `Order`, which then flows through the existing `PaymentService` → Stripe path. No new webhook branch is needed — the Order is handled identically to a cart-originated order.

```
QuoteService ──(accept)──► OrderService.createFromQuote() ──► PaymentService (Stripe)
                                                                     │
                                                              existing webhook
                                                              handles Order
```

### Package Structure

```
io.k2dv.garden
  ├─ b2b
  │    ├─ model/          Company, CompanyMembership, CompanyRole
  │    ├─ repository/
  │    ├─ service/        CompanyService
  │    ├─ dto/
  │    └─ controller/     CompanyController
  │
  └─ quote
       ├─ model/          QuoteCart, QuoteCartItem, QuoteRequest, QuoteItem, QuoteStatus
       ├─ repository/
       ├─ service/        QuoteCartService, QuoteService, QuotePdfService
       ├─ dto/
       └─ controller/     QuoteCartController, QuoteController, AdminQuoteController
```

---

## Data Model

### Existing Change

`catalog.product_variants.price` becomes **nullable**. A `NULL` price means the variant is quote-only and cannot be added to the regular cart. The `CartService` must validate that `price IS NOT NULL` before allowing an add-to-cart.

---

### New Schema: `b2b`

```
Company
  ├─ id (UUIDv7)
  ├─ name (String, not null)
  ├─ taxId (String, nullable)
  ├─ phone (String, nullable)
  ├─ billingAddressLine1 (String, nullable)
  ├─ billingAddressLine2 (String, nullable)
  ├─ billingCity (String, nullable)
  ├─ billingState (String, nullable)
  ├─ billingPostalCode (String, nullable)
  ├─ billingCountry (String, nullable)
  ├─ createdAt, updatedAt

CompanyMembership
  ├─ id (UUIDv7)
  ├─ companyId (FK → Company)
  ├─ userId (UUID, FK → User)
  ├─ role: OWNER | MEMBER
  └─ createdAt
  [unique on (companyId, userId)]
```

---

### New Schema: `quote`

```
QuoteCart
  ├─ id (UUIDv7)
  ├─ userId (UUID — one ACTIVE per user, enforced by partial unique index)
  ├─ status: ACTIVE | SUBMITTED
  ├─ createdAt, updatedAt
  └─< QuoteCartItem
        ├─ id (UUIDv7)
        ├─ quoteCartId (FK → QuoteCart)
        ├─ variantId (UUID, FK → ProductVariant)
        ├─ quantity (int, min 1)
        ├─ note (String, nullable)
        └─ createdAt, updatedAt

QuoteRequest
  ├─ id (UUIDv7)
  ├─ userId (UUID — the requestor)
  ├─ companyId (UUID, FK → Company)
  ├─ assignedStaffId (UUID, nullable — user with staff role)
  ├─ status: PENDING | ASSIGNED | DRAFT | SENT | ACCEPTED | REJECTED | EXPIRED | CANCELLED
  ├─ deliveryAddressLine1 (String, not null)
  ├─ deliveryAddressLine2 (String, nullable)
  ├─ deliveryCity (String, not null)
  ├─ deliveryState (String, nullable)
  ├─ deliveryPostalCode (String, not null)
  ├─ deliveryCountry (String, not null)
  ├─ shippingRequirements (text, nullable)
  ├─ customerNotes (text, nullable)
  ├─ staffNotes (text, nullable — internal, not shown to customer)
  ├─ expiresAt (Instant, nullable — set by staff when sending)
  ├─ pdfBlobId (UUID, nullable — FK → BlobObject, set after PDF generated)
  ├─ orderId (UUID, nullable — FK → Order, set when user accepts)
  ├─ createdAt, updatedAt
  └─< QuoteItem
        ├─ id (UUIDv7)
        ├─ quoteRequestId (FK → QuoteRequest)
        ├─ variantId (UUID, nullable — null for custom one-off line items)
        ├─ description (String — product title or custom item description)
        ├─ quantity (int, min 1)
        ├─ unitPrice (BigDecimal, nullable — null until staff sets it)
        └─ createdAt, updatedAt
```

---

## Status State Machine

```
PENDING
  └─(staff assigns)──────────► ASSIGNED
       └─(staff edits/drafts)─► DRAFT
            └─(staff sends)────► SENT
                 ├─(user accepts)─► ACCEPTED ──(Stripe webhook: PAID)──► Order.status = PAID
                 ├─(user rejects)─► REJECTED
                 ├─(expiresAt passes, lazy)──► EXPIRED
                 └─(staff cancels)─► CANCELLED

PENDING / ASSIGNED / DRAFT
  └─(staff cancels)──────────► CANCELLED
```

**Rules:**
- `ACCEPTED` is set the moment the user calls accept and a Stripe Checkout Session is created. `QuoteRequest.orderId` is populated at this point.
- Expiry is **lazy**: checked at accept time. No background scheduler. If `expiresAt` is in the past, the quote transitions to `EXPIRED` and the accept returns `409 Conflict`.
- Payment confirmation flows through the existing Stripe webhook — the created `Order` is handled identically to a cart-originated order.
- All unit prices on `QuoteItem`s must be non-null before staff can call the send endpoint (`422 Unprocessable Entity` otherwise).

---

## API Surface

### B2B — Companies

All require `@Authenticated`.

```
POST   /api/v1/companies                        — create company (requestor becomes OWNER)
GET    /api/v1/companies                        — list companies the current user belongs to
GET    /api/v1/companies/{id}                   — get company detail
PUT    /api/v1/companies/{id}                   — update company info (OWNER only)

POST   /api/v1/companies/{id}/members           — add member by email { email } (OWNER only)
DELETE /api/v1/companies/{id}/members/{userId}  — remove member (OWNER only)
```

### Quote Cart

All require `@Authenticated`.

```
GET    /api/v1/quote-cart                       — get active quote cart (creates if none)
DELETE /api/v1/quote-cart                       — clear/reset active quote cart

POST   /api/v1/quote-cart/items                 — add item { variantId, quantity, note }
PUT    /api/v1/quote-cart/items/{itemId}         — update item { quantity, note }
DELETE /api/v1/quote-cart/items/{itemId}         — remove item
```

### Quote Requests — Customer

All require `@Authenticated`.

```
POST   /api/v1/quotes                           — submit quote request from active quote cart
                                                  body: { companyId, deliveryAddress, shippingRequirements, customerNotes }

GET    /api/v1/quotes                           — list own quote requests (paginated)
GET    /api/v1/quotes/{id}                      — get quote detail + line items

POST   /api/v1/quotes/{id}/accept               — accept quote → creates Order → returns { checkoutUrl, orderId }
POST   /api/v1/quotes/{id}/reject               — reject quote
```

### Quote Requests — Admin/Staff

```
GET    /api/v1/admin/quotes                         — list all quotes (filter: status, companyId, assignedStaffId) [quote:read]
GET    /api/v1/admin/quotes/{id}                    — quote detail + line items [quote:read]

PUT    /api/v1/admin/quotes/{id}/assign             — assign to staff { staffUserId } [quote:write]
PUT    /api/v1/admin/quotes/{id}/items/{itemId}     — edit line item { unitPrice, quantity } [quote:write]
POST   /api/v1/admin/quotes/{id}/items              — add custom line item { description, quantity, unitPrice } [quote:write]
DELETE /api/v1/admin/quotes/{id}/items/{itemId}     — remove line item [quote:write]
PUT    /api/v1/admin/quotes/{id}/notes              — update staffNotes [quote:write]

POST   /api/v1/admin/quotes/{id}/send               — generate PDF, email to user, transition → SENT [quote:write]
                                                      body: { expiresAt }
POST   /api/v1/admin/quotes/{id}/cancel             — cancel quote [quote:write]
```

---

## Flows

### Quote Submission

1. User has an `ACTIVE` QuoteCart with at least one item.
2. User calls `POST /api/v1/quotes` with company ID and delivery details.
3. `QuoteCartItem`s are copied to `QuoteItem`s (all `unitPrice` null).
4. `QuoteCart` transitions to `SUBMITTED`. A new `ACTIVE` QuoteCart is created lazily on next access.
5. `QuoteRequest` created with status `PENDING`.
6. Confirmation email sent to the user.

### Quote Send (Staff)

1. Staff has set `unitPrice` on all `QuoteItem`s. System validates no nulls before proceeding.
2. Staff calls `POST /api/v1/admin/quotes/{id}/send` with `{ expiresAt }`.
3. `QuotePdfService.generate()` renders Thymeleaf HTML template → OpenHTMLtoPDF → `byte[]`.
4. PDF uploaded via `BlobService` → `pdfBlobId` set on `QuoteRequest`.
5. PDF emailed to user as attachment via `EmailService`.
6. `QuoteRequest` transitions to `SENT`, `expiresAt` set.

### Quote Accept (User → Order → Stripe)

1. User calls `POST /api/v1/quotes/{id}/accept`.
2. Expiry check: if `expiresAt` is past, transition to `EXPIRED`, return `409 Conflict`.
3. `QuoteService` calls `OrderService.createFromQuote(quoteRequest)`:
   - Creates `Order` (status `PENDING_PAYMENT`) with `OrderItem`s from `QuoteItem`s.
   - Reserves inventory for each item.
4. `QuoteService` calls `PaymentService.createCheckoutSession(order)` — same as cart checkout path.
5. `QuoteRequest.orderId` set; status → `ACCEPTED`.
6. Returns `{ checkoutUrl, orderId }`.

---

## PDF Generation

**Library:** OpenHTMLtoPDF

**Service:** `QuotePdfService`

```java
byte[] generate(QuoteRequest quote, List<QuoteItem> items, Company company);
```

**Template:** `quote-template.html` (Thymeleaf), rendered to HTML string first, then passed to `PdfRendererBuilder`.

**Template contents:**
- Company name, billing address, tax ID
- Quote reference number and issue date
- Expiry date
- Delivery address and shipping requirements
- Line items table: description, quantity, unit price, line total
- Grand total
- Staff notes (if present, shown as "Notes from our team")
- Branding / logo (URL from `AppProperties`)

**Email:** Existing `EmailService`. Quote email uses a dedicated template; PDF attached as `quote-{id}.pdf`.

---

## Permissions

| Resource | Actions | Assigned to |
|---|---|---|
| `quote` | `read` | `staff` role |
| `quote` | `write` | `staff` role |
| `quote` | `assign` | `staff` role |

The `staff` role is created via the existing IAM system. Only a superuser can assign the `staff` role to a user.

---

## Database Migrations

**V15 — B2B schema**
- New schema: `b2b`
- Tables: `b2b.companies`, `b2b.company_memberships`
- Unique index on `b2b.company_memberships(company_id, user_id)`

**V16 — Quote schema**
- New schema: `quote`
- Tables: `quote.quote_carts`, `quote.quote_cart_items`, `quote.quote_requests`, `quote.quote_items`
- Unique partial index: `CREATE UNIQUE INDEX ON quote.quote_carts (user_id) WHERE status = 'ACTIVE'`
- Alter: `catalog.product_variants.price` → nullable

---

## Testing Strategy

- **CompanyService** — integration tests with Testcontainers PostgreSQL, `@Transactional` + `@Rollback`
- **QuoteCartService, QuoteService** — integration tests; cover submission, send validation (null price guard), accept (expiry check, order creation), reject, cancel
- **QuotePdfService** — unit test: render a known fixture quote, assert PDF bytes non-empty
- **OrderService** — existing tests extended to cover `createFromQuote()` path
- **Controller slice tests** — `@WebMvcTest` for `CompanyController`, `QuoteCartController`, `QuoteController`, `AdminQuoteController`

---

## Summary

| Concern | Decision |
|---|---|
| Quote cart | Separate from regular cart; same partial unique index pattern |
| Quote-only products | `ProductVariant.price` nullable; null = quote-only, blocked from regular cart |
| B2B company | Self-service creation; user can belong to multiple companies; OWNER/MEMBER roles |
| Staff role | IAM role (`staff`) with `quote:read/write/assign` permissions; superuser-assigned |
| Quote → payment | Accept creates a real `Order` via `OrderService.createFromQuote()` → existing Stripe path |
| Expiry | Staff-set `expiresAt`; lazy check at accept time; no background job |
| PDF | OpenHTMLtoPDF from Thymeleaf HTML template; stored in blob storage; emailed as attachment |
| Webhook | No changes — existing webhook handles the `Order` as normal |
