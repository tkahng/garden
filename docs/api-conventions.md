# API Conventions

All endpoints are under `/api/v1/`. The API is JSON-only (`Content-Type: application/json`) except for file upload endpoints which use `multipart/form-data`.

---

## Base URL

| Environment | Base URL |
|---|---|
| Local dev | `http://localhost:8080/api/v1` |
| Production | configured via reverse proxy |

---

## Authentication

Almost all non-public endpoints require a JWT access token. Pass it as a Bearer token:

```
Authorization: Bearer <access_token>
```

Tokens are obtained from `/api/v1/auth/login` or `/api/v1/auth/register`. They expire after **15 minutes**. Use `/api/v1/auth/refresh` with the refresh token to get a new access token.

Guest endpoints (cart, checkout) use an `X-Guest-Session` header instead:

```
X-Guest-Session: <uuid>
```

The client generates this UUID and persists it locally (e.g., in localStorage).

---

## Response Envelope

All responses are wrapped in `ApiResponse<T>`:

```json
{
  "data": { ... },
  "message": null
}
```

Error responses:

```json
{
  "data": null,
  "message": "Human-readable error description"
}
```

The HTTP status code carries the semantic meaning. The `message` field is for display to users or developers.

---

## Pagination

Paginated endpoints return `PagedResult<T>`:

```json
{
  "content": [ ... ],
  "page": 0,
  "size": 20,
  "totalElements": 143,
  "totalPages": 8,
  "last": false
}
```

Query parameters:
- `page` — zero-indexed (default `0`)
- `size` — items per page (default varies by endpoint, usually `20`)

---

## HTTP Status Codes

| Code | Meaning |
|---|---|
| 200 | OK — successful GET, PUT, POST returning data |
| 201 | Created — resource created (rare; most POSTs return 200) |
| 204 | No Content — successful DELETE or action with no body |
| 400 | Bad Request — validation failure; `message` explains what failed |
| 401 | Unauthorized — missing or invalid JWT |
| 403 | Forbidden — valid JWT but insufficient permissions |
| 404 | Not Found — resource doesn't exist |
| 409 | Conflict — business rule violation (duplicate, state mismatch) |
| 422 | Unprocessable Entity — request is structurally valid but logically invalid |
| 429 | Too Many Requests — rate limit exceeded |
| 500 | Internal Server Error — unexpected server failure |

---

## Permission-Gated Endpoints

Admin endpoints check permissions via `@HasPermission("resource:action")`. The permission name is listed in the API tables in the spec files. Permissions are assigned to IAM roles; roles are assigned to users.

The built-in roles and their permissions:

| Role | What it can do |
|---|---|
| CUSTOMER | Storefront only — no admin access |
| STAFF | Read-only admin access across most resources |
| MANAGER | Full admin access except IAM management |
| OWNER | Full access including IAM |

Permission names follow the pattern `resource:action` where action is one of `read`, `write`, `delete`, `manage`.

---

## Rate Limiting

- **Global:** per-IP rate limit applied by `ApiRateLimitFilter` to all API endpoints
- **Login:** additional per-email rate limit (1 attempt/minute) applied by `LoginRateLimiter` to `POST /auth/request-password-reset`

Clients that exceed limits receive `429 Too Many Requests`.

---

## Filtering and Sorting

Admin list endpoints accept query parameters for filtering. Common patterns:

```
GET /api/v1/admin/orders?status=PAID&userId=<uuid>&page=0&size=20
GET /api/v1/admin/users?email=foo@example.com&status=ACTIVE
GET /api/v1/admin/products?titleContains=planter&status=ACTIVE
```

Sorting is endpoint-specific and documented in the specs. Where supported, the `sortBy` parameter accepts field names.

---

## Storefront vs Admin Split

Endpoints are split into two audiences:

| Audience | Path prefix | Auth |
|---|---|---|
| Storefront (customer-facing) | `/api/v1/*` | JWT (`@Authenticated`) or no auth |
| Admin (staff-facing) | `/api/v1/admin/*` | JWT + permission (`@HasPermission`) |

A logged-in customer cannot access admin endpoints even with a valid JWT — the permission check blocks them.

---

## Idempotency

Stripe webhook processing is idempotent via the `ProcessedStripeEvent` table (V59 migration). Other write endpoints are not idempotent by default — clients should not retry POST requests on network failure without checking the current state first.

---

## Dates and Times

All timestamps are ISO-8601 UTC strings in request and response bodies:

```
"createdAt": "2026-05-26T10:30:00Z"
```

The database stores all timestamps in UTC (`UTC` timezone set on the JDBC driver). The JVM runs in UTC.

---

## Currency

Monetary amounts are `NUMERIC(19,4)` in the database — returned as strings in JSON to avoid floating-point precision loss. The currency field defaults to `"usd"` (lowercase ISO-4217).

---

## File Uploads

Blob upload: `POST /api/v1/blobs` — `multipart/form-data`, field name `file`. Max file size 20 MB, max request 25 MB.

The response includes a `key` field. Use the key to reference the blob from other entities (e.g., `featuredImageId` on a product).

---

## Versioning

The API is currently at v1 (`/api/v1/`). There is no v2. Breaking changes require coordination across the frontend apps.
