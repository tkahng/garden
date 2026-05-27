/**
 * Spring Security integration layer for the auth module. Contains the JWT bearer-token
 * converter that validates incoming RS256 access tokens, custom security annotations
 * ({@code @Authenticated}, {@code @HasPermission}) for declarative access control,
 * and the {@code @CurrentUser} argument resolver that injects the authenticated principal
 * directly into controller method parameters.
 */
package io.k2dv.garden.auth.security;
