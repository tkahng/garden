# Infrastructure Spec — Blob, Webhooks, Stats, Audit, Scheduler, Automation

**Status:** Current  
**Last updated:** 2026-05-26  
**Packages:** `io.k2dv.garden.blob`, `io.k2dv.garden.webhook`, `io.k2dv.garden.stats`, `io.k2dv.garden.audit`, `io.k2dv.garden.scheduler`, `io.k2dv.garden.automation`

---

## Blob Storage

### BlobObject
`storage.blob_objects`

| Field | Constraint | Notes |
|---|---|---|
| key | String, unique, not null | S3 object key |
| filename | not null | original upload name |
| contentType | not null | MIME type |
| size | long, not null | bytes |
| alt, title | nullable | image metadata |
| width, height | Integer, nullable | images only |
| folder | nullable | organizational prefix |

**Storage backend:** AWS S3 via `S3StorageService` (`StorageService` interface). Presigned URLs for uploads.

### API — Blob (`/api/v1/blobs`, `@Authenticated` for write)

| Method | Path | Description |
|---|---|---|
| POST | `/` | Multipart upload → returns BlobObject with key |
| GET | `/{key}` | Serve/redirect to file |
| DELETE | `/{key}` | Delete blob and S3 object |

---

## Outbound Webhooks

### WebhookEndpoint
`webhook.endpoints`

| Field | Constraint | Notes |
|---|---|---|
| url | not null | receiver URL |
| secret | not null | used for HMAC-SHA256 signing |
| description | nullable | |
| events | text[] | subscribed WebhookEventType values |
| isActive | boolean, default true | |

### WebhookDelivery
`webhook.deliveries`

| Field | Notes |
|---|---|
| endpointId | FK |
| eventType | WebhookEventType |
| payload | jsonb |
| status | WebhookDeliveryStatus |
| attemptCount | int |
| lastAttemptedAt | Instant, nullable |
| nextRetryAt | Instant, nullable |
| httpStatus | Integer, nullable |
| responseBody | text, nullable |

**WebhookEventType enum:**  
`ORDER_PLACED` · `ORDER_PAID` · `ORDER_CANCELLED` · `ORDER_REFUNDED` · `FULFILLMENT_SHIPPED` · `FULFILLMENT_DELIVERED` · `INVOICE_ISSUED` · `INVOICE_PAID` · `INVOICE_OVERDUE`

**WebhookDeliveryStatus enum:** `PENDING` · `SUCCESS` · `FAILED`

### API — Admin Webhooks (`/api/v1/admin/webhooks`)

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `webhook:read` | List endpoints |
| POST | `/` | `webhook:write` | Create endpoint |
| GET | `/{id}` | `webhook:read` | Get endpoint |
| PUT | `/{id}` | `webhook:write` | Update endpoint |
| DELETE | `/{id}` | `webhook:delete` | Delete endpoint |
| GET | `/{id}/deliveries` | `webhook:read` | Delivery history |
| POST | `/deliveries/{id}/retry` | `webhook:write` | Retry failed delivery |

### WebhookDispatchService
- Signs payload with `HMAC-SHA256(secret, body)` → header `X-Webhook-Signature`
- Retry: exponential backoff; max attempts configurable
- `OutboundWebhookService` queues deliveries; `WebhookDispatchService` executes HTTP POST

---

## Stats

### AdminStatsController (`/api/v1/admin/stats`, `@HasPermission("stats:read")`)

| Method | Path | Query Params | Response |
|---|---|---|---|
| GET | `/` | `from`, `to` (Instant) | `{ orderCount, totalRevenue, averageOrderValue, newCustomerCount }` |
| GET | `/time-series` | `from`, `to` | `List<{ date, orderCount, revenue }>` (daily buckets) |
| GET | `/top-products` | `from`, `to`, `limit` | `List<{ productId, title, handle, quantitySold, revenue }>` |
| GET | `/top-customers` | `from`, `to`, `limit` | `List<{ userId, email, firstName, lastName, orderCount, totalSpent }>` |

All aggregate only orders with status PAID / PARTIALLY_FULFILLED / FULFILLED.

---

## Audit Log

### AuditLog
`shared.audit_log` — extends `ImmutableBaseEntity`

| Field | Notes |
|---|---|
| actorId | UUID of user who performed the action |
| actorEmail | snapshot of email at time of action |
| action | verb (e.g., `CREATE`, `UPDATE`, `DELETE`) |
| entityType | e.g., `Company`, `Product` |
| entityId | PK of affected entity |
| beforeJson | jsonb — entity state before (nullable) |
| afterJson | jsonb — entity state after (nullable) |

**`@Audited` AOP annotation** on service methods triggers `AuditAspect` to capture before/after state and write an `AuditLog` record.

### AdminAuditLogController (`/api/v1/admin/audit-log`, `@HasPermission("audit:read")`)

| Method | Path | Query Params | Description |
|---|---|---|---|
| GET | `/` | `entityType`, `entityId`, `actorEmail`, `page`, `size` | List audit records, sorted by `createdAt DESC` |

---

## Scheduled Jobs (ShedLock)

All schedulers use ShedLock for distributed safety — only one node executes at a time.

### ExpiryScheduler

| Job | Cron | Logic |
|---|---|---|
| `expireQuotes()` | every 15 min (`0 */15 * * * *`) | Bulk-update SENT quotes where `expiresAt < now` → EXPIRED; sends expiry notification email |
| `expireInvoices()` | configurable | Bulk-update invoices past `dueAt` → OVERDUE via `InvoiceRepository.markOverduePastDue(now)` |
| `expireGiftCards()` | configurable | Deactivates expired gift cards |
| `expireDiscounts()` | configurable | Marks expired discounts inactive |

### AutomationScheduler

| Job | Cron | Logic |
|---|---|---|
| `sendAbandonedCartReminders()` | hourly (`0 0 * * * *`) | Finds ACTIVE carts older than configured delay (e.g., 24h) with no `abandonedReminderSentAt`; marks ABANDONED; sends reminder email; sets `abandonedReminderSentAt` |
| `sendLowStockAlerts()` | configurable | Finds inventory levels below threshold with no recent alert; sends alert; sets `lowStockAlertedAt` |

### PaymentReconciliationScheduler

| Job | Cron | Logic |
|---|---|---|
| `reconcileStalePendingPayments()` | every 10 min (`0 */10 * * * *`) | Finds orders in PENDING_PAYMENT for >15 min with a `stripeSessionId`; calls Stripe API to verify status; if `complete` → transitions to PAID and publishes `OrderConfirmedEvent`; if spike ≥ 10 unresolved → logs warning (possible webhook outage) |

---

## Automation — AutoTagService

Called on every `OrderConfirmedEvent` (i.e., after payment).

**Logic — `applyOrderTags(userId)`:**

1. Count user's confirmed orders (status: PAID / PARTIALLY_FULFILLED / FULFILLED)
2. Sum total spend across those orders

**Tag rules (applied to `User.tags` array):**

| Tag | Condition |
|---|---|
| `first-time-buyer` | orderCount == 1 |
| `repeat-customer` | orderCount >= 2 (also removes `first-time-buyer`) |
| `loyal-customer` | orderCount >= 5 |
| `vip` | totalSpend >= configured threshold |

Tags are additive; `repeat-customer` replaces `first-time-buyer` when threshold crossed.
