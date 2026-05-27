/**
 * Business-logic services for the auth module. {@link io.k2dv.garden.auth.service.AuthService}
 * is the primary entry point covering registration, login, logout, email verification, and
 * password management. Supporting services handle JWT minting ({@link io.k2dv.garden.auth.service.JwtService}),
 * token lifecycle ({@link io.k2dv.garden.auth.service.TokenService}), transactional email
 * delivery ({@link io.k2dv.garden.auth.service.EmailService} /
 * {@link io.k2dv.garden.auth.service.SmtpEmailService}), and admin impersonation
 * ({@link io.k2dv.garden.auth.service.ImpersonationService}).
 */
package io.k2dv.garden.auth.service;
