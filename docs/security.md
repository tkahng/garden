# Security

## Overview

Garden uses JWT-based stateless auth for API access. Access tokens are short-lived RS256 JWTs. Refresh tokens are opaque, stored hashed in Postgres, and rotated on every use.

---

## Authentication Flows

### Email/Password (CREDENTIALS)

```
POST /api/v1/auth/register
  → creates User (UNVERIFIED) + Identity (CREDENTIALS)
  → assigns CUSTOMER role
  → sends EMAIL_VERIFICATION token (24h TTL) via email
  → returns { accessToken, refreshToken }

POST /api/v1/auth/login
  → validates email + passwordHash
  → rejects if UserStatus == SUSPENDED
  → returns { accessToken, refreshToken }
```

### Google OAuth2

```
GET /oauth2/authorization/google
  → Spring Security redirects to Google consent screen

GET /login/oauth2/code/google   (Google callback)
  → OAuth2SuccessHandler runs:
      → upserts User (created or found by email)
      → upserts Identity (GOOGLE provider + accountId)
      → mints token pair
      → redirects to {FRONTEND_URL}/auth/callback?accessToken=...&refreshToken=...
```

### Email Verification

```
GET /api/v1/auth/verify-email?token={rawToken}
  → validates TOKEN record (type=EMAIL_VERIFICATION, not expired)
  → sets User.emailVerifiedAt = now
  → sets User.status = ACTIVE
  → deletes token record (consumed)
```

### Password Reset

```
POST /api/v1/auth/request-password-reset  { email }
  → rate-limited: 1 request/min per email address
  → sends PASSWORD_RESET token (24h TTL) via email
  → response is always 204 (enumeration prevention)

POST /api/v1/auth/confirm-password-reset/{rawToken}  { newPassword }
  → validates TOKEN record
  → updates passwordHash on Identity
  → deletes token record
```

---

## Access Tokens

**Format:** RS256-signed JWT (Nimbus JOSE + JWT)  
**TTL:** 15 minutes  
**Claims:**

| Claim | Value |
|---|---|
| `sub` | user UUID |
| `email` | user email |
| `permissions` | string array e.g. `["product:read","order:write"]` |
| `impersonatedBy` | admin UUID (impersonation tokens only) |

The public key is injected at startup via `app.jwt.public-key` (base64 DER). Spring Security's OAuth2 resource server validates every request automatically.

---

## Refresh Tokens

**Format:** opaque UUID (raw value returned to client once)  
**Storage:** SHA-256 hash stored in `auth.refresh_tokens`  
**TTL:** 30 days  

### Token Rotation

Every `POST /api/v1/auth/refresh` call:
1. Validates the presented token hash exists and is not revoked
2. Issues a new refresh token
3. Sets `revokedAt` on the old token; sets `replacedByToken` to the new raw value (for chain audit)
4. Returns the new token pair

### Replay Detection

If a **revoked** refresh token is presented (e.g., a stolen token used after it was already rotated):
- The entire token family is revoked (all tokens in the `replacedByToken` chain)
- The attacker and the legitimate user are both logged out
- No silent failure

---

## One-Time Tokens

Used for email verification and password reset. Stored in `auth.tokens`.

| Type | TTL |
|---|---|
| `EMAIL_VERIFICATION` | 24 hours |
| `PASSWORD_RESET` | 24 hours |

All one-time tokens are:
- Generated as a UUID
- Stored as SHA-256 hash (raw value emailed to user)
- Deleted on successful consumption (`validateAndConsume` is atomic validate + delete)

---

## Roles and Permissions

### IAM Model

```
User ──(many-to-many)──► Role ──(many-to-many)──► Permission
```

Permissions have a `resource:action` name (e.g., `product:write`, `order:read`).

### Built-in Roles

These roles are seeded by Flyway migrations and cannot be deleted via the API:

| Role | Description |
|---|---|
| `CUSTOMER` | Default role assigned on registration. Storefront access only. |
| `STAFF` | Admin read access. Can view orders, products, users but not modify. |
| `MANAGER` | Full admin access. Cannot manage IAM (roles/permissions). |
| `OWNER` | Full access to everything including IAM. Gets all permissions implicitly. |

### `OWNER` shortcut

`IamService.loadPermissionsForUser(userId)` checks if the user has the `OWNER` role. If yes, all existing permissions are returned — no need to enumerate individual assignments. This is cached in Caffeine (10-minute TTL, max 1000 entries).

### Custom Roles

New roles can be created via `POST /api/v1/admin/iam/roles`. Permissions are assigned to roles, and roles are assigned to users via the admin user endpoints. The cache is evicted on every role assignment change.

---

## Method-Level Authorization

Two custom annotations enforce auth at the controller method level:

**`@Authenticated`** — requires a valid JWT (any user). Applied to storefront endpoints that need identity but not a specific permission.

**`@HasPermission("resource:action")`** — requires a valid JWT where the `permissions` claim contains the named permission. Applied to all admin endpoints.

These are checked by Spring AOP / Spring Security. A valid JWT without the required permission returns `403 Forbidden`.

---

## Impersonation

Admin users (MANAGER or OWNER) can impersonate a customer for debugging:

```
POST /api/v1/admin/users/{targetUserId}/impersonate
  → validates caller has user:write permission
  → validates target is not STAFF/MANAGER/OWNER (staff cannot be impersonated)
  → creates ImpersonationToken (30 min TTL, SHA-256 hashed)
  → returns { impersonationToken, targetUserId }
```

The impersonation token is a special short-lived JWT with an additional `impersonatedBy` claim. `ImpersonationTokenValidationFilter` intercepts requests with this token and:
- Records `usedAt` on first use (single use)
- Rejects if already used or expired

Impersonation is fully audited via the `@Audited` aspect.

---

## Security Hardening Notes

- Passwords: bcrypt hashed via Spring Security `PasswordEncoder`
- SQL injection: not possible — all queries use JPA/Spring Data (parameterized)
- CORS: configured to allow the frontend origins only
- HTTPS: enforced at the reverse proxy level (Dokploy/Caddy); `server.forward-headers-strategy=framework` trusts `X-Forwarded-Proto`
- File upload: size capped at 20 MB per file, 25 MB per request
- Rate limiting: per-IP global; per-email for password reset
- Stripe webhook validation: `WebhookController` verifies the `Stripe-Signature` header using the webhook secret before processing any event
