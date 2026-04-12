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
  ├─ status: PENDING | ASSIGNED | DRAFT | SENT | ACCEPTED | PAID | REJECTED | EXPIRED | CANCELLED
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
                 ├─(user accepts)─► ACCEPTED ──(Stripe webhook: checkout.session.completed)──► PAID
                 ├─(user rejects)─► REJECTED
                 ├─(expiresAt passes, lazy)──► EXPIRED
                 └─(staff/user cancels)─► CANCELLED

PENDING / ASSIGNED / DRAFT / SENT
  └─(staff/user cancels)──────► CANCELLED
```

**Rules:**
- `ACCEPTED` is set the moment the user calls accept and a Stripe Checkout Session is created. `QuoteRequest.orderId` is populated at this point.
- `PAID` is set by the Stripe webhook handler (`checkout.session.completed`) after successful payment. `PaymentService` looks up the quote by `orderId` from session metadata and transitions it from `ACCEPTED` → `PAID`. The linked `Order` is also set to `PAID` by the same webhook.
- Expiry is **lazy**: checked at accept time. No background scheduler. If `expiresAt` is in the past, the quote transitions to `EXPIRED` and the accept returns `409 Conflict`.
- Payment confirmation flows through the existing Stripe webhook — the created `Order` is handled identically to a cart-originated order.
- All unit prices on `QuoteItem`s must be non-null before staff can call the send endpoint (`422 Unprocessable Entity` otherwise).
- `ACCEPTED`, `PAID`, `REJECTED`, `EXPIRED`, and `CANCELLED` are terminal — no further mutations allowed.

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

GET    /api/v1/quotes/{id}/pdf                  — download quote PDF as attachment (only if pdfBlobId set)
                                                  returns 404 PDF_NOT_AVAILABLE if PDF not yet generated
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
6. Confirmation email sent to the user (`sendQuoteSubmitted`).
7. If `app.adminNotificationEmail` is configured, a notification email is sent to that address (`sendQuoteNewRequest`).

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
4. `QuoteService` calls `PaymentService.createCheckoutSessionFromQuote(order, items, quote)` — sets `automatic_tax`, delivery address, and stores `orderId` in Stripe session metadata.
5. `QuoteRequest.orderId` set; status → `ACCEPTED`.
6. Returns `{ checkoutUrl, orderId }`.

### Quote Payment (Stripe Webhook → PAID)

1. User completes payment on Stripe hosted checkout page.
2. Stripe fires `checkout.session.completed` webhook to `POST /api/v1/webhooks/stripe`.
3. `PaymentService.handleWebhook` processes the event:
   - Calls `orderService.confirmPayment(sessionId, paymentIntentId)` → `Order.status = PAID`, inventory reservations released.
   - Reads `orderId` from session metadata; looks up quote via `QuoteRequestRepository.findByOrderId`.
   - If quote is in `ACCEPTED` status, transitions it to `PAID`.
4. User's quote detail page now shows status `PAID` with a link to the order.

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
- Subtotal (sum of line totals)
- Tax note: "Taxes will be calculated at checkout" — no tax amount on the PDF (see Taxes section)
- Staff notes (if present, shown as "Notes from our team")
- Branding / logo (URL from `AppProperties`)

**Email:** Existing `EmailService`. Quote email uses a dedicated template; PDF attached as `quote-{id}.pdf`.

**PDF download:** After the PDF has been generated and sent, the customer can re-download it at any time via `GET /api/v1/quotes/{id}/pdf`. `QuoteService.downloadPdf()` verifies ownership, guards on `pdfBlobId != null`, fetches bytes from `StorageService.fetch(key)`, and returns them. `StorageService` interface exposes `InputStream fetch(String key)` implemented by `S3StorageService` via `GetObjectRequest`.

---

## Taxes

**Approach:** Stripe Tax (external engine). The quote PDF shows pre-tax prices only. Tax is calculated and collected by Stripe at checkout time.

**Rationale:** Tax rates vary by delivery jurisdiction and product type. Storing tax on the quote would require maintaining a tax rule engine. Stripe Tax handles this automatically using the delivery address and product tax codes already provided at Checkout Session creation.

**How it works:**

1. The PDF shows a subtotal and the note "Taxes will be calculated at checkout." No tax line on the PDF.
2. When the user accepts the quote, `PaymentService.createCheckoutSessionFromQuote()` creates the Stripe Checkout Session with:
   ```json
   {
     "automatic_tax": { "enabled": true },
     "customer_details": {
       "address": { ... delivery address from QuoteRequest ... },
       "address_source": "shipping"
     }
   }
   ```
3. Stripe computes and displays the applicable tax on its hosted checkout page.
4. The Stripe webhook `checkout.session.completed` event contains `total_details.amount_tax` — this can be stored on the `Order` if a tax audit trail is needed (optional, tracked separately).

**No schema changes required** for the quote domain. Tax amount storage on `Order` is a separate concern.

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
- **QuotePdfService** — integration test with real Thymeleaf context:
  - Assert PDF bytes start with `%PDF` (valid PDF)
  - Assert PDF bytes non-empty for null expiry case
  - **Content correctness:** build a quote with two line items where staff has set unit prices. Extract text from PDF bytes using Apache PDFBox `PDFTextStripper`. Assert that item descriptions, quantities, formatted unit prices, formatted line totals, and grand total all appear in the extracted text.
- **OrderService** — existing tests extended to cover `createFromQuote()` path
- **Controller slice tests** — `@WebMvcTest` for `CompanyController`, `QuoteCartController`, `QuoteController`, `AdminQuoteController`
  - `QuoteController`: add cases for `GET /{id}/pdf` — happy path (200 + `application/pdf` content type + `Content-Disposition: attachment`), PDF not yet generated (404 `PDF_NOT_AVAILABLE`), wrong owner (403)

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
| PDF | OpenHTMLtoPDF from Thymeleaf HTML template; stored in blob storage; emailed as attachment; re-downloadable via `GET /api/v1/quotes/{id}/pdf` |
| Taxes | Stripe Tax (`automatic_tax: enabled`) at Checkout Session creation; PDF shows subtotal + note; no tax stored on quote |
| Webhook | No changes — existing webhook handles the `Order` as normal |
