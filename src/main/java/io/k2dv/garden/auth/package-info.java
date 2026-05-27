/**
 * The auth module owns the entire authentication and authorization surface for the platform.
 * It handles JWT-based access tokens (RS256), rotating refresh tokens, single-use one-time
 * tokens (email verification, password reset), email/password identity management, OAuth2
 * social login, per-request rate limiting, and administrator impersonation of customer
 * accounts.
 */
package io.k2dv.garden.auth;
