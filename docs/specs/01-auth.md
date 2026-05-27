# Auth & IAM Spec

**Status:** Current  
**Last updated:** 2026-05-26  
**Packages:** `io.k2dv.garden.auth`, `io.k2dv.garden.iam`, `io.k2dv.garden.admin.iam`

---

## Entities

### User
`auth.users` — `io.k2dv.garden.user.model.User`

| Field | Type | Constraint |
|---|---|---|
| email | String | unique, not null |
| firstName, lastName | String | not null |
| phone | String | nullable |
| status | UserStatus | not null |
| emailVerifiedAt | Instant | nullable |
| adminNotes | text | nullable |
| tags | text[] | nullable |
| metadata | jsonb | nullable |
| roles | ManyToMany → Role | lazy |

**UserStatus enum:** `UNVERIFIED` · `ACTIVE` · `SUSPENDED`

---

### Identity
`auth.identities` — `io.k2dv.garden.auth.model.Identity`

| Field | Constraint |
|---|---|
| userId | not null, FK |
| provider | IdentityProvider enum, not null |
| accountId | not null, unique with provider |
| passwordHash | nullable (CREDENTIALS only) |
| accessToken, refreshToken, idToken | nullable (OAuth only) |
| expiresAt | Instant, nullable |

**IdentityProvider enum:** `CREDENTIALS` · `GOOGLE`

---

### RefreshToken
`auth.refresh_tokens` — `io.k2dv.garden.auth.model.RefreshToken`

| Field | Constraint | Notes |
|---|---|---|
| tokenHash | unique, not null | SHA-256 hex of raw token |
| userId | not null | |
| expiresAt | not null | |
| revokedAt | nullable | set on consumption or revocation |
| replacedByToken | nullable | raw value kept for chain audit |

**Token rotation:** on use, old token is revoked and `replacedByToken` is set. If a revoked token is presented again, the entire family is revoked (replay detection).

---

### Token (one-time tokens)
`auth.tokens` — `io.k2dv.garden.auth.model.Token`

| Field | Constraint |
|---|---|
| userId | not null |
| type | TokenType enum, not null |
| tokenHash | unique, not null |
| expiresAt | not null |

**TokenType enum:** `REFRESH_TOKEN` · `EMAIL_VERIFICATION` · `PASSWORD_RESET`

---

### ImpersonationToken
`auth.impersonation_tokens`

| Field | Notes |
|---|---|
| targetUserId | who is being impersonated |
| adminUserId | who initiated |
| tokenHash | SHA-256 hex, unique |
| expiresAt | now + 30 min |
| usedAt | recorded on first use |

**Rules:** Cannot impersonate STAFF/MANAGER/OWNER. Token is single-use (usedAt set on first check).

---

### Role & Permission
`auth.roles`, `auth.permissions`

| Entity | Key Fields |
|---|---|
| Role | name (unique), description, permissions (ManyToMany) |
| Permission | name (unique), resource (String), action (String) |

**Predefined roles (cannot be deleted):** `CUSTOMER` · `STAFF` · `MANAGER` · `OWNER`

---

### Address
`auth.addresses`

| Field | Notes |
|---|---|
| userId | FK, not null |
| firstName, lastName, company | |
| address1, address2, city, province, zip, country | |
| isDefault | boolean |

---

## API — Auth (`/api/v1/auth`)

| Method | Path | Auth | Description |
|---|---|---|---|
| POST | `/register` | none | Creates user, assigns CUSTOMER role, sends email verification |
| POST | `/login` | none | Validates credentials, returns token pair |
| POST | `/refresh` | none | Rotates refresh token, returns new token pair |
| POST | `/logout` | none | Revokes refresh token (idempotent) |
| GET | `/verify-email?token=` | none | Consumes EMAIL_VERIFICATION token, activates user |
| POST | `/resend-verification` | none | Resends verification email (silent if not found) |
| POST | `/request-password-reset` | none | Rate-limited (1/min/email); sends reset token |
| POST | `/confirm-password-reset/{token}` | none | Consumes PASSWORD_RESET token, updates password |
| GET | `/check-email?email=` | none | Returns `{ exists: Boolean }` |
| POST | `/update-password` | `@Authenticated` | Validates current password, sets new |

---

## API — Admin IAM (`/api/v1/admin/iam`)

All require `@HasPermission("iam:manage")`.

| Method | Path | Description |
|---|---|---|
| GET | `/roles` | List all roles |
| POST | `/roles` | Create role |
| PUT | `/roles/{id}` | Update role name/description |
| DELETE | `/roles/{id}` | Delete role (predefined roles blocked) |
| GET | `/permissions` | List all permissions |
| POST | `/roles/{id}/permissions` | Assign permission to role |
| DELETE | `/roles/{id}/permissions/{permissionId}` | Remove permission from role |

---

## API — Admin Users (`/api/v1/admin/users`)

| Method | Path | Permission | Description |
|---|---|---|---|
| GET | `/` | `user:read` | List/search users (filterable: email, status, tags, name) |
| GET | `/{id}` | `user:read` | Get user |
| PUT | `/{id}` | `user:write` | Update user fields |
| PUT | `/{id}/notes` | `user:write` | Update admin notes |
| PUT | `/{id}/tags` | `user:write` | Update tags array |
| POST | `/{id}/roles` | `user:write` | Assign role |
| DELETE | `/{id}/roles/{roleId}` | `user:write` | Remove role |
| POST | `/{id}/impersonate` | `user:write` | Mint impersonation token |

---

## Service Logic

### AuthService
- **register:** creates user (UNVERIFIED), creates CREDENTIALS identity, assigns CUSTOMER role, sends EMAIL_VERIFICATION token
- **login:** validates password hash, checks status (SUSPENDED → reject), checks emailVerifiedAt (warns if unverified), mints token pair
- **refresh:** delegates to TokenService.rotateRefreshToken, reloads permissions, mints new access token
- **verifyEmail:** TokenService.validateAndConsume(EMAIL_VERIFICATION), sets emailVerifiedAt, status → ACTIVE
- **requestPasswordReset:** rate-limited per email via LoginRateLimiter; silent response (enumeration prevention)
- **confirmPasswordReset:** TokenService.validateAndConsume(PASSWORD_RESET), updates passwordHash on identity

### JwtService
- Access tokens: RS256-signed, claims include `email`, `permissions[]`, `sub` (userId)
- Impersonation tokens: additional `impersonatedBy` claim (adminUserId)
- TTL is configurable via application properties

### TokenService
- All tokens stored as SHA-256 hash; raw token returned to caller once
- `validateAndConsume`: atomic validate + delete
- `rotateRefreshToken`: issues replacement, revokes prior, records `replacedByToken`; replay triggers full-family revocation

### IamService
- `loadPermissionsForUser(userId)`: cached per user; OWNER role returns all permissions
- Cache evicted on role assignment/removal

### OAuth2 (Google)
- `OAuth2SuccessHandler`: on successful OAuth2 login, upserts User and Identity records; mints token pair; redirects with tokens in query params
