/**
 * JPA entities and enumerations for the auth module. Includes the {@code Identity} entity
 * (linking a user to an authentication provider such as CREDENTIALS or OAuth2), token
 * entities for one-time and refresh tokens, and the {@code ImpersonationToken} audit record.
 * The {@code TokenType} and {@code IdentityProvider} enums define the allowed values for
 * discriminator columns used across these tables.
 */
package io.k2dv.garden.auth.model;
