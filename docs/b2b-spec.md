# B2B Backend Spec

**Status:** Retroactive — documents what is built and what remains  
**Last updated:** 2026-04-19  
**Package root:** `io.k2dv.garden`  
**DB schemas:** `b2b`, `quote`  
**Flyway migrations:** V15, V16, V18, V28, V29, V30, V32

---

## Overview

The B2B system lets verified companies buy on negotiated terms. The core path is:

```
Company created → price lists configured → buyer builds quote cart
→ submits quote request → admin prices items → admin sends quote
→ buyer accepts → order + invoice created → admin records payments
```

Net terms (credit accounts) let qualifying companies accept quotes without upfront payment. The invoice draws on a credit line and is settled manually by the admin recording wire transfers or cheques.

---

## Database Schema

All B2B tables live in the `b2b` Postgres schema. Quote tables live in the `quote` schema.

### b2b schema tables

| Table | Created | Purpose |
|---|---|---|
| `companies` | V15 | Organisation entity |
| `company_memberships` | V15 | User↔company with role + spending limit |
| `company_invitations` | V32 | Email invitations with UUID token |
| `price_lists` | V28 | Named contract price sets per company |
| `price_list_entries` | V28 | Per-variant custom prices with quantity tiers |
| `credit_accounts` | V30 | Net-terms credit line (one per company) |
| `invoices` | V30 | B2B invoices (one per order) |
| `invoice_payments` | V30 | Immutable payment records against an invoice |

### quote schema tables

| Table | Created | Purpose |
|---|---|---|
| `quote_carts` | V16 | Active/submitted quote cart per user |
| `quote_cart_items` | V16 | Items in a cart (variantId + qty + note) |
| `quote_requests` | V16 | Submitted quote (10-state lifecycle) |
| `quote_items` | V16 | Line items on a quote (description + pricing) |

---

## Entities

### Company
`b2b.companies` — `io.k2dv.garden.b2b.model.Company`

| Field | Column | Constraint | Notes |
|---|---|---|---|
| id | id | PK, UUID | from BaseEntity |
| name | name | NOT NULL | |
| taxId | tax_id | nullable | VAT / EIN |
| phone | phone | nullable | |
| billingAddressLine1 | billing_address_line1 | nullable | |
| billingAddressLine2 | billing_address_line2 | nullable | |
| billingCity | billing_city | nullable | |
| billingState | billing_state | nullable | |
| billingPostalCode | billing_postal_code | nullable | |
| billingCountry | billing_country | nullable | |
| createdAt / updatedAt | | | from BaseEntity |

**Create:** `@NotBlank name` required. All address fields optional.  
**Update:** same shape as create (`UpdateCompanyRequest`).

---

### CompanyMembership
`b2b.company_memberships` — `io.k2dv.garden.b2b.model.CompanyMembership`

| Field | Column | Constraint |
|---|---|---|
| companyId | company_id | NOT NULL, FK |
| userId | user_id | NOT NULL, FK |
| role | role | NOT NULL, ENUM (default MEMBER) |
| spendingLimit | spending_limit | nullable, NUMERIC(19,4), CHECK > 0 |

**Unique index:** `(company_id, user_id)` — a user can only have one membership per company.

**Roles:**
- `OWNER` — full control, set on company creation; cannot be reassigned via invitation
- `MANAGER` — can invite members, approve spending, manage price lists
- `MEMBER` — can submit quotes; optionally subject to a spending limit

---

### CompanyInvitation
`b2b.company_invitations` — `io.k2dv.garden.b2b.model.CompanyInvitation`

| Field | Column | Constraint | Notes |
|---|---|---|---|
| companyId | company_id | NOT NULL, FK | |
| email | email | NOT NULL | |
| role | role | NOT NULL, ENUM | MANAGER or MEMBER only — OWNER cannot be invited |
| spendingLimit | spending_limit | nullable | pre-set on invitation |
| token | token | NOT NULL, UNIQUE | UUID used in accept URL |
| invitedBy | invited_by | NOT NULL, FK | user who sent it |
| status | status | NOT NULL, ENUM (default PENDING) | |
| expiresAt | expires_at | NOT NULL | set to now + 7 days |

**Statuses:** `PENDING` · `ACCEPTED` · `CANCELLED` · `EXPIRED`

**Business rules:**
- Only OWNER or MANAGER can invite
- Cannot invite if a PENDING invitation already exists for that email+company (duplicate check: `existsByCompanyIdAndEmailAndStatus`)
- Accepting validates that the accepting user's email matches the invitation email
- Accepting a token that is expired throws an error

---

### CreditAccount
`b2b.credit_accounts` — `io.k2dv.garden.b2b.model.CreditAccount`

| Field | Column | Constraint | Default |
|---|---|---|---|
| companyId | company_id | NOT NULL, UNIQUE FK | |
| creditLimit | credit_limit | NOT NULL, NUMERIC(19,4) | |
| paymentTermsDays | payment_terms_days | NOT NULL | 30 |
| currency | currency | NOT NULL | USD |

**One per company** — the `company_id` column has a unique constraint.  
Currency is set at creation and cannot be changed afterward.

**Computed fields** (not persisted):
- `outstandingBalance` — JPQL aggregate: `SUM(totalAmount - paidAmount)` for invoices with status IN (`ISSUED`, `PARTIAL`, `OVERDUE`)
- `availableCredit` = `creditLimit − outstandingBalance`

**Business rules:**
- `@Positive creditLimit` required on create
- `paymentTermsDays` defaults to 30 if not supplied
- `delete` removes the credit account; outstanding invoices are not automatically voided (server enforces nothing — caller must verify balance is clear)

---

### PriceList
`b2b.price_lists` — `io.k2dv.garden.b2b.model.PriceList`

| Field | Column | Constraint | Default |
|---|---|---|---|
| companyId | company_id | NOT NULL, FK | |
| name | name | NOT NULL | |
| currency | currency | NOT NULL | USD |
| priority | priority | NOT NULL | 0 |
| startsAt | starts_at | nullable | no restriction |
| endsAt | ends_at | nullable | no expiry |

A price list without `startsAt`/`endsAt` is always active.

---

### PriceListEntry
`b2b.price_list_entries` — `io.k2dv.garden.b2b.model.PriceListEntry`

| Field | Column | Constraint | Default |
|---|---|---|---|
| priceListId | price_list_id | NOT NULL, FK | |
| variantId | variant_id | NOT NULL, FK | |
| price | price | NOT NULL, NUMERIC(19,4) | |
| minQty | min_qty | NOT NULL | 1 |

**Unique index:** `(price_list_id, variant_id, min_qty)` — enables quantity-tiered pricing.  
`@PositiveOrZero price`, `@Min(1) minQty`.

**Upsert semantics:** `upsertEntry` finds by `(priceListId, variantId, minQty)` — updates price if found, creates new entry otherwise. Each `PUT /entries/{variantId}` targets one tier.  
**Delete semantics:** `deleteEntry` removes **all** entries for a variant across all quantity tiers, not just one tier.

---

### Price Resolution Algorithm

`PriceListService.resolvePrice(companyId, variantId, qty)`:

1. Load active price lists for the company: `startsAt <= now AND endsAt > now` (or null bounds), ordered by `priority DESC`
2. Query `PriceListEntry` candidates: `priceListId IN activeListIds AND variantId = ? AND minQty <= qty`, ordered by `minQty DESC`
3. `pickBestEntry`: iterate active lists by priority; for each list find the highest `minQty` candidate — first match wins
4. If no contract price found, fall back to the variant's default `price` field

Returns: `ResolvedPriceResponse { price, isContractPrice }`.

This is also called at quote-submission time to pre-populate item prices.

---

### Invoice
`b2b.invoices` — `io.k2dv.garden.b2b.model.Invoice`

| Field | Column | Constraint | Default |
|---|---|---|---|
| companyId | company_id | NOT NULL, FK | |
| orderId | order_id | NOT NULL, UNIQUE FK | one invoice per order |
| quoteId | quote_id | nullable, FK | link back to source quote |
| status | status | NOT NULL, ENUM | ISSUED |
| totalAmount | total_amount | NOT NULL, NUMERIC(19,4) | |
| paidAmount | paid_amount | NOT NULL, NUMERIC(19,4) | 0 |
| currency | currency | NOT NULL | USD |
| issuedAt | issued_at | NOT NULL | |
| dueAt | due_at | NOT NULL | issuedAt + paymentTermsDays |

**Statuses:** `ISSUED` → `PARTIAL` → `PAID`  (side paths: `OVERDUE`, `VOID`)

**Status transitions:**

| From | Action | To | Side effect |
|---|---|---|---|
| ISSUED / PARTIAL / OVERDUE | recordPayment (partial) | PARTIAL | paidAmount updated |
| ISSUED / PARTIAL / OVERDUE | recordPayment (full) | PAID | Order set to PAID |
| ISSUED / PARTIAL | markOverdue | OVERDUE | — |
| ISSUED / INVOICED (order) | voidInvoice | VOID | Order cancelled if INVOICED |

**recordPayment validation:** `amount` must be `> 0` and `<= outstandingAmount` (otherwise throws).

---

### InvoicePayment
`b2b.invoice_payments` — `io.k2dv.garden.b2b.model.InvoicePayment`

Extends `ImmutableBaseEntity` — no `updatedAt`, records are never modified after creation.

| Field | Notes |
|---|---|
| invoiceId | FK to invoice |
| amount | NOT NULL, positive |
| paymentReference | wire ref, cheque #, etc. |
| notes | free text |
| paidAt | when the payment was actually made (not when recorded) |

---

### QuoteRequest
`quote.quote_requests` — `io.k2dv.garden.quote.model.QuoteRequest`

| Field | Column | Constraint |
|---|---|---|
| userId | user_id | NOT NULL, FK |
| companyId | company_id | NOT NULL, FK |
| assignedStaffId | assigned_staff_id | nullable |
| status | status | NOT NULL, ENUM (default PENDING) |
| deliveryAddressLine1 | delivery_address_line1 | NOT NULL |
| deliveryAddressLine2 | delivery_address_line2 | nullable |
| deliveryCity | delivery_city | NOT NULL |
| deliveryState | delivery_state | nullable |
| deliveryPostalCode | delivery_postal_code | NOT NULL |
| deliveryCountry | delivery_country | NOT NULL |
| shippingRequirements | shipping_requirements | nullable |
| customerNotes | customer_notes | nullable |
| staffNotes | staff_notes | nullable |
| expiresAt | expires_at | nullable; `@Future` enforced on send |
| pdfBlobId | pdf_blob_id | nullable; set when PDF is generated |
| orderId | order_id | nullable; set on acceptance |
| approverId | approver_id | nullable; set when manager approves |
| approvedAt | approved_at | nullable |

**Quote statuses:**

```
PENDING ──► ASSIGNED ──► DRAFT ──► SENT ──► ACCEPTED ──► PAID
  │            │           │         │
  └────────────┴───────────┴──► CANCELLED
                                     │
                           SENT ──► EXPIRED (scheduled)
                           SENT ──► REJECTED (buyer)
                    ACCEPTED ──► PENDING_APPROVAL (spending limit)
               PENDING_APPROVAL ──► ACCEPTED (manager approves)
               PENDING_APPROVAL ──► REJECTED (manager rejects)
```

**Editable states** (items/notes can be changed): PENDING, ASSIGNED, DRAFT  
**Sendable states** (can call `/send`): PENDING, ASSIGNED, DRAFT  
**Cancellable states** (admin or buyer): PENDING, ASSIGNED, DRAFT, SENT  
**Terminal states** (no further mutations): ACCEPTED, PAID, REJECTED, EXPIRED, CANCELLED, PENDING_APPROVAL

---

### QuoteItem
`quote.quote_items` — `io.k2dv.garden.quote.model.QuoteItem`

| Field | Notes |
|---|---|
| quoteRequestId | FK |
| variantId | nullable — custom line items may not map to a variant |
| description | NOT NULL |
| quantity | NOT NULL |
| unitPrice | nullable — null means "pending pricing" (buyer sees placeholder) |

**Send validation:** all items must have a non-null `unitPrice` before the quote can be sent.  
**Submit pre-population:** on quote submission, `PriceListService.resolvePrice` is called for each cart item that has a `variantId`; the resolved price is stored as `unitPrice` on the quote item.

---

### QuoteCart
`quote.quote_carts` — `io.k2dv.garden.quote.model.QuoteCart`

| Field | Notes |
|---|---|
| userId | NOT NULL, FK |
| status | ACTIVE or SUBMITTED |

**One active cart per user** — DB unique partial index on `(user_id)` where `status = 'ACTIVE'`.  
`getOrCreateActiveCart` lazily creates a cart on first use.  
On quote submission the cart transitions to `SUBMITTED`; a new `ACTIVE` cart is created on the next `addItem` call.

---

## Service Business Logic

### CompanyService

- **create:** creates company, immediately creates an OWNER membership for the creating user
- **getById:** verifies the requesting user is a member before returning (`requireMemberAccess`)
- **update:** only OWNER can update company details
- **addMember:** OWNER or MANAGER only; adds membership directly (no invitation); looks user up by email
- **removeMember:** OWNER or MANAGER only; cannot remove the OWNER
- **updateMemberRole:** OWNER or MANAGER only; cannot change OWNER role
- **updateSpendingLimit:** OWNER or MANAGER only; sets/clears spending limit on a membership
- **requireMemberAccess:** throws 403 if the user has no membership in the company

### CompanyInvitationService

- **invite:** requires OWNER or MANAGER; `role` cannot be OWNER; checks for duplicate PENDING invitation; sets `expiresAt = now + 7 days`; sends invitation email
- **accept:** validates token exists and is PENDING; checks `expiresAt`; validates accepting user's email matches invitation email; creates membership; marks invitation ACCEPTED
- **cancel:** requires OWNER or MANAGER; only PENDING invitations can be cancelled
- **listPending:** returns invitations with status PENDING for a company; requires membership

### CreditAccountService

- **create:** validates company exists; only one credit account per company (unique constraint enforced by DB)
- **getByCompany:** computes `outstandingBalance` via `InvoiceRepository.computeOutstandingBalance`; computes `availableCredit = creditLimit - outstandingBalance`
- **update:** updates `creditLimit` and `paymentTermsDays`; currency is not updatable

### PriceListService

- **listByCompany:** ordered by `priority DESC`
- **listEntries:** ordered by `minQty ASC`
- **upsertEntry:** uses `findByPriceListIdAndVariantIdAndMinQty` — updates existing or creates new
- **deleteEntry:** `deleteByPriceListIdAndVariantId` removes ALL tiers for a variant, not just one

### InvoiceService

- **createFromOrder:** called internally by `QuoteService.finalizeAcceptance`; `dueAt = issuedAt + paymentTermsDays days`; sets Order status to INVOICED
- **recordPayment:** validates `amount <= outstanding`; creates `InvoicePayment`; updates `paidAmount`; if `paidAmount >= totalAmount` → PAID and sets Order to PAID; else → PARTIAL
- **voidInvoice:** sets status to VOID; if Order is still INVOICED, cancels it

### QuoteService

**submit:**
1. Verifies `companyId` membership for the user
2. Loads the user's ACTIVE cart; throws if empty
3. Creates `QuoteRequest` with delivery address from the request
4. For each cart item: calls `resolvePrice(companyId, variantId, qty)` to pre-populate `unitPrice`
5. Marks cart SUBMITTED
6. Sends confirmation email to user + notification to configured admin email

**send:**
1. Quote must be in PENDING / ASSIGNED / DRAFT
2. All `QuoteItem.unitPrice` must be non-null (throws if any are null)
3. Generates PDF via `QuotePdfService` (Thymeleaf + openhtmltopdf), uploads to blob storage, stores `pdfBlobId`
4. Sends email to buyer with PDF attachment
5. Transitions to SENT, sets `expiresAt`

**accept (buyer):**
1. Quote must be SENT
2. Checks `expiresAt` — throws if expired
3. **Spending limit check:** if the membership has a `spendingLimit` and quote total > spendingLimit → transitions to PENDING_APPROVAL, returns `{ pendingApproval: true }`
4. Otherwise calls `finalizeAcceptance`

**finalizeAcceptance:**
1. Creates an Order from the quote items
2. Checks if company has a credit account
   - If yes: checks `availableCredit >= totalAmount` (throws if insufficient); calls `InvoiceService.createFromOrder`; returns `{ invoiceId }`
   - If no: creates Stripe checkout session; returns `{ checkoutUrl }`
3. Sets `QuoteRequest.orderId`, transitions to ACCEPTED

**approveSpend (manager):**
1. Quote must be PENDING_APPROVAL
2. Requesting user must be OWNER or MANAGER of the company
3. Sets `approverId`, `approvedAt`; calls `finalizeAcceptance`

**rejectSpend (manager):**
1. Quote must be PENDING_APPROVAL
2. Requesting user must be OWNER or MANAGER
3. Transitions to REJECTED

**assign:** transitions PENDING → ASSIGNED; sets `assignedStaffId`

**Scheduled expiry** (bulk): `QuoteRequestRepository.expireByStatus(SENT, EXPIRED, now)` — updates all SENT quotes where `expiresAt < now`. Caller is a scheduled job (not shown in source read, assumed to exist).

---

## API Reference

### Storefront — Company (`/api/v1/companies`)

All endpoints require `@Authenticated`.

| Method | Path | Permission | Description |
|---|---|---|---|
| POST | `/` | any user | Create company; caller becomes OWNER |
| GET | `/` | any user | List companies the caller belongs to |
| GET | `/{id}` | member | Get company (membership required) |
| PUT | `/{id}` | OWNER | Update company details |
| GET | `/{id}/members` | member | List all members |
| POST | `/{id}/members` | OWNER/MANAGER | Add member directly by email |
| DELETE | `/{id}/members/{userId}` | OWNER/MANAGER | Remove member |
| PUT | `/{id}/members/{userId}/role` | OWNER/MANAGER | Change member role |
| PUT | `/{id}/members/{userId}/spending-limit` | OWNER/MANAGER | Set spending limit |
| GET | `/{id}/invitations` | OWNER/MANAGER | List pending invitations |
| POST | `/{id}/invitations` | OWNER/MANAGER | Send invitation |
| DELETE | `/{id}/invitations/{invId}` | OWNER/MANAGER | Cancel invitation |
| GET | `/{id}/invoices` | member | List company invoices |
| GET | `/{id}/price-lists` | member | List company price lists |
| GET | `/{id}/price-lists/{plId}/entries` | member | List entries (with product details) |
| GET | `/{id}/price` | member | Resolve best price for a variant+qty |

### Storefront — Invitations (`/api/v1/invitations`)

| Method | Path | Auth | Description |
|---|---|---|---|
| GET | `/{token}` | none | Look up invitation by token (for accept page) |
| POST | `/{token}/accept` | `@Authenticated` | Accept invitation; email must match |

### Storefront — Quote Cart (`/api/v1/quote-cart`)

| Method | Path | Description |
|---|---|---|
| GET | `/` | Get (or create) active cart |
| DELETE | `/` | Clear all items from active cart |
| POST | `/items` | Add item (upserts if variant already in cart) |
| PUT | `/items/{itemId}` | Update quantity and/or note |
| DELETE | `/items/{itemId}` | Remove item |

### Storefront — Quotes (`/api/v1/quotes`)

| Method | Path | Description |
|---|---|---|
| POST | `/` | Submit quote from active cart |
| GET | `/` | List caller's quotes (paginated) |
| GET | `/pending-approvals` | List PENDING_APPROVAL quotes for OWNER/MANAGER |
| GET | `/{id}` | Get quote (ownership check) |
| POST | `/{id}/accept` | Accept SENT quote |
| POST | `/{id}/reject` | Reject SENT quote |
| POST | `/{id}/cancel` | Cancel quote (PENDING/ASSIGNED/DRAFT/SENT) |
| POST | `/{id}/approve` | Manager approves PENDING_APPROVAL quote |
| POST | `/{id}/reject-approval` | Manager rejects PENDING_APPROVAL quote |
| GET | `/{id}/pdf` | Download quote PDF (ownership check) |

### Admin — Price Lists (`/api/v1/admin/price-lists`)

Permission required per endpoint:

| Method | Path | Permission |
|---|---|---|
| POST | `/` | `price_list:write` |
| GET | `/` | `price_list:read` — **requires `?companyId=` query param** |
| GET | `/{id}` | `price_list:read` |
| PUT | `/{id}` | `price_list:write` |
| DELETE | `/{id}` | `price_list:delete` |
| GET | `/{id}/entries` | `price_list:read` |
| PUT | `/{id}/entries/{variantId}` | `price_list:write` |
| DELETE | `/{id}/entries/{variantId}` | `price_list:delete` |

### Admin — Credit Accounts (`/api/v1/admin/credit-accounts`)

| Method | Path | Permission |
|---|---|---|
| POST | `/` | `credit_account:write` |
| GET | `/company/{companyId}` | `credit_account:read` |
| PUT | `/company/{companyId}` | `credit_account:write` |
| DELETE | `/company/{companyId}` | `credit_account:write` |

### Admin — Invoices (`/api/v1/admin/invoices`)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/` | `invoice:read` | Filterable: `companyId`, `status`; paginated |
| GET | `/{id}` | `invoice:read` | |
| POST | `/{id}/payments` | `invoice:write` | Record a payment |
| POST | `/{id}/overdue` | `invoice:write` | Mark overdue |
| DELETE | `/{id}` | `invoice:delete` | Void invoice |

### Admin — Quotes (`/api/v1/admin/quotes`)

| Method | Path | Permission | Notes |
|---|---|---|---|
| GET | `/` | `quote:read` | Filter: `status`, `companyId`, `assignedStaffId` |
| GET | `/{id}` | `quote:read` | |
| POST | `/{id}/assign` | `quote:write` | Set staff |
| POST | `/{id}/items` | `quote:write` | Add line item |
| PUT | `/{id}/items/{itemId}` | `quote:write` | Update qty + price |
| DELETE | `/{id}/items/{itemId}` | `quote:write` | Remove item |
| PUT | `/{id}/notes` | `quote:write` | Update staff notes |
| POST | `/{id}/send` | `quote:write` | Send to buyer; requires all items priced |
| POST | `/{id}/cancel` | `quote:write` | Cancel quote |

---

## Permissions Matrix

Permissions are seeded by Flyway migrations and assigned to IAM roles.

| Permission | STAFF | MANAGER | OWNER |
|---|---|---|---|
| `quote:read` | ✓ | ✓ | ✓ |
| `quote:write` | ✓ | ✓ | ✓ |
| `price_list:read` | ✓ | ✓ | ✓ |
| `price_list:write` | | ✓ | ✓ |
| `price_list:delete` | | ✓ | ✓ |
| `credit_account:read` | ✓ | ✓ | ✓ |
| `credit_account:write` | | ✓ | ✓ |
| `invoice:read` | ✓ | ✓ | ✓ |
| `invoice:write` | | ✓ | ✓ |
| `invoice:delete` | | ✓ | ✓ |

*MANAGER/OWNER here refer to admin IAM roles (staff-side), not company membership roles.*

---

## PDF Generation

`QuotePdfService.generate(QuoteRequest, List<QuoteItem>, Company)`:

- Template: `quote-template` (Thymeleaf HTML)
- Renderer: openhtmltopdf
- Calculates: line totals (`qty × unitPrice`), grand total
- Date format: UTC `yyyy-MM-dd`
- Output: `byte[]`; uploaded to blob storage by `QuoteService.send`; `pdfBlobId` stored on `QuoteRequest`

---

## Scheduled / Background Work

The following bulk-update queries exist but their scheduler wiring is not covered by this audit:

| Query | Repository method | Trigger |
|---|---|---|
| Expire SENT quotes past `expiresAt` | `QuoteRequestRepository.expireByStatus(SENT, EXPIRED, now)` | Scheduled job (assumed) |
| Bulk-mark invoices overdue past `dueAt` | `InvoiceRepository.markOverduePastDue(now)` | Scheduled job (assumed) |

---

## Validation Summary

| DTO | Key constraints |
|---|---|
| `CreateCompanyRequest` | `@NotBlank name` |
| `AddMemberRequest` | `@Email @NotBlank email`, `@Positive spendingLimit` (optional) |
| `CreateInvitationRequest` | `@Email @NotBlank email`; role cannot be OWNER |
| `CreateCreditAccountRequest` | `@NotNull companyId`, `@Positive creditLimit` |
| `UpdateCreditAccountRequest` | `@Positive creditLimit` |
| `CreatePriceListRequest` | `@NotNull companyId`, `@NotBlank name` |
| `UpsertPriceListEntryRequest` | `@PositiveOrZero price`, `@Min(1) minQty` |
| `RecordPaymentRequest` | `@Positive amount`; server also validates `amount <= outstanding` |
| `SubmitQuoteRequest` | `@NotNull companyId`; all delivery fields required except line2/state |
| `SendQuoteRequest` | `@NotNull @Future expiresAt`; all items must have `unitPrice` (service check) |
| `AddQuoteItemRequest` | `@NotBlank description`, `@Min(1) quantity`, `@NotNull unitPrice` |

---

## Known Gaps and Open Items

### Missing features

| # | Area | Description |
|---|---|---|
| 1 | Companies | No admin endpoint to list/search companies (only storefront `GET /companies` which scopes to the caller's memberships) |
| 2 | Companies | No admin endpoint to create companies on behalf of a customer |
| 3 | Invoices | No admin endpoint to create invoices manually (invoices are only auto-created via `finalizeAcceptance`) |
| 4 | Invoices | `markOverduePastDue` bulk query exists but no scheduled `@Scheduled` annotation found; overdue marking may require a manual admin call per invoice |
| 5 | Quotes | `expireByStatus` bulk query exists but scheduler wiring not confirmed |
| 6 | Price Lists | `deleteEntry` removes ALL tiers for a variant, not a single tier — no way to delete just one tier of a tiered entry |
| 7 | Invitations | No re-send endpoint; cancelled invitations require a new invite |
| 8 | Credit | No guard preventing deletion of a credit account with outstanding invoices |
| 9 | Quotes | `AddQuoteItemRequest` requires `@NotNull unitPrice` — admin cannot add an "unpriced" item (unitPrice must be provided upfront) |
| 10 | Quotes | No endpoint to clone an existing quote |

### Hardening / correctness

| # | Description |
|---|---|
| 11 | `voidInvoice` cancels the linked order only when order status is INVOICED; if the order has already moved to another state, it is left as-is with no error |
| 12 | `recordPayment` does not guard against concurrent payments exceeding the outstanding balance (no optimistic locking or database-level lock on the invoice row) |
| 13 | `resolvePrice` falls back to `variant.price` if no contract entry matches, but there is no explicit handling for the case where `variant.price` is also null |
| 14 | Quote submission pre-populates `unitPrice` from price lists, but admin can overwrite it afterward with any value — there is no audit trail of the original contract price vs. manually entered price |
| 15 | `CompanyInvitationService.accept` validates the accepting user's email matches the invitation email, but a user can change their email after an invitation is sent — no re-validation on send |
