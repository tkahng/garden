# ADR-0001: JWT Over Server-Side Sessions

**Status:** Accepted

## Context

Garden serves three distinct clients: the customer storefront (`garden-web`), the admin panel (`garden-admin`), and third-party API consumers via the outbound webhook system. All three need to authenticate requests to the same backend. The backend is designed to be horizontally scalable — multiple instances behind a load balancer — and may eventually serve mobile clients.

Server-side sessions require either sticky load balancing or a shared session store (Redis, database). Both add operational complexity and infrastructure coupling. Spring Security supports both models.

## Decision

Use short-lived JWT access tokens (signed with a symmetric HMAC-SHA256 secret) for request authentication, with a longer-lived refresh token stored in an `HttpOnly` cookie to allow silent re-authentication.

Permissions are encoded directly in the JWT claims (role + explicit permission set). The `JwtAuthFilter` validates the signature and expiry on every request without a database lookup.

## Consequences

**Positive:**
- Stateless — any backend instance can verify any token without shared state.
- Works identically for browser clients (cookie-based refresh) and API consumers (bearer token).
- No session store to operate or scale.

**Negative:**
- Tokens cannot be individually revoked before expiry. Mitigation: short access token TTL (15 min); logout invalidates the refresh cookie client-side.
- Permission changes take up to one TTL window to propagate to existing tokens.
- Secret rotation requires coordinated redeployment.
