/**
 * Spring Data JPA repositories for the auth module's persistence layer. Provides
 * data access for identity credentials, one-time tokens, rotating refresh tokens,
 * and impersonation audit records. Repositories in this package are used exclusively
 * by auth-domain services and must not be referenced from other modules directly.
 */
package io.k2dv.garden.auth.repository;
