package io.k2dv.garden.auth.service;

import io.k2dv.garden.auth.dto.*;
import io.k2dv.garden.auth.model.Identity;
import io.k2dv.garden.auth.model.IdentityProvider;
import io.k2dv.garden.auth.model.TokenType;
import io.k2dv.garden.auth.repository.IdentityRepository;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.iam.service.IamService;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.UnauthorizedException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core authentication service responsible for the full identity lifecycle: registration,
 * login, logout, email verification, and password management. Applies enumeration
 * protection (silent no-ops on unknown emails) and in-process rate limiting for
 * password-reset requests. Collaborates with {@link TokenService} for one-time tokens,
 * {@link JwtService} for access-token minting, and {@link IamService} for RBAC role
 * assignment at registration time.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final Duration PASSWORD_RESET_RATE_LIMIT = Duration.ofMinutes(1);

    /**
     * Tracks the last password-reset request time per email address.
     * Guards against enumeration-style abuse. Each entry is a lightweight
     * Instant; the map is bounded by the number of distinct email addresses
     * seen (tolerable for a typical deployment).
     */
    private final ConcurrentHashMap<String, Instant> passwordResetLastRequest = new ConcurrentHashMap<>();

    private final UserRepository userRepo;
    private final IdentityRepository identityRepo;
    private final TokenService tokenService;
    private final JwtService jwtService;
    private final IamService iamService;
    private final EmailService emailService;
    private final CartService cartService;
    private final AppProperties props;
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * Creates a new user account with CREDENTIALS identity, assigns the CUSTOMER role,
     * sends an email-verification link, and returns a fresh access + refresh token pair.
     * Throws {@code ConflictException} if the email address is already in use.
     */
    @Transactional
    public AuthTokenResponse register(RegisterRequest req) {
        if (userRepo.existsByEmail(req.email())) {
            throw new ConflictException("EMAIL_TAKEN", "An account with this email already exists");
        }

        User user = new User();
        user.setEmail(req.email());
        user.setFirstName(req.firstName());
        user.setLastName(req.lastName());
        user.setStatus(UserStatus.UNVERIFIED);
        user = userRepo.save(user);

        Identity identity = new Identity();
        identity.setUserId(user.getId());
        identity.setProvider(IdentityProvider.CREDENTIALS);
        identity.setAccountId(user.getId().toString());
        identity.setPasswordHash(passwordEncoder.encode(req.password()));
        identityRepo.save(identity);

        iamService.assignRoleByName(user.getId(), "CUSTOMER");

        String verificationToken = tokenService.createToken(
            user.getId(), TokenType.EMAIL_VERIFICATION,
            props.getJwt().getEmailVerificationTtl());
        emailService.sendEmailVerification(user.getEmail(), verificationToken);

        return mintTokenPair(user);
    }

    /**
     * Authenticates a user by email and password and returns a new token pair.
     * Throws {@code UnauthorizedException} using a generic message for both bad
     * credentials and unknown emails to prevent account enumeration. Throws
     * {@code ForbiddenException} if the account is suspended.
     */
    @Transactional
    public AuthTokenResponse login(LoginRequest req) {
        return login(req, null);
    }

    @Transactional
    public AuthTokenResponse login(LoginRequest req, UUID guestSessionId) {
        User user = userRepo.findByEmail(req.email())
            .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password"));

        Identity identity = identityRepo.findByUserIdAndProvider(user.getId(), IdentityProvider.CREDENTIALS)
            .orElseThrow(() -> new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password"));

        if (!passwordEncoder.matches(req.password(), identity.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Invalid email or password");
        }

        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "Your account has been suspended");
        }

        AuthTokenResponse tokens = mintTokenPair(user);

        if (guestSessionId != null) {
            try {
                cartService.mergeGuestCartIntoUserCart(guestSessionId, user.getId());
            } catch (Exception e) {
                log.error("Failed to merge guest cart {} into user cart for user {}",
                    guestSessionId, user.getId(), e);
            }
        }

        return tokens;
    }

    /**
     * Rotates the refresh token (invalidating the old one) and issues a new access + refresh
     * token pair. Detects token-reuse attacks by revoking all active sessions for the user
     * if a previously-consumed token is presented.
     */
    @Transactional
    public AuthTokenResponse refresh(RefreshRequest req) {
        TokenService.RotatedRefreshToken rotated =
            tokenService.rotateRefreshToken(req.refreshToken(), props.getAuth().getRefreshTokenExpiry());
        User user = userRepo.findById(rotated.userId())
            .orElseThrow(() -> new UnauthorizedException("USER_NOT_FOUND", "User not found"));
        if (user.getStatus() == UserStatus.SUSPENDED) {
            throw new ForbiddenException("ACCOUNT_SUSPENDED", "Your account has been suspended");
        }
        List<String> permissions = iamService.loadPermissionsForUser(user.getId());
        String accessToken = jwtService.mintAccessToken(user, permissions);
        return new AuthTokenResponse(accessToken, rotated.newRawToken());
    }

    /**
     * Revokes the refresh token to terminate the session. Safe to call with an already-revoked
     * or unknown token — the operation is idempotent and never throws.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        // Revoke the rotating refresh token; no-op if already revoked or not found
        tokenService.revokeRefreshToken(rawRefreshToken);
    }

    /**
     * Consumes a single-use email-verification token and transitions the user's status from
     * UNVERIFIED to ACTIVE. Throws {@code UnauthorizedException} if the token is invalid or
     * expired, and {@code NotFoundException} if the associated user no longer exists.
     */
    @Transactional
    public void verifyEmail(String rawToken) {
        UUID userId = tokenService.validateAndConsume(rawToken, TokenType.EMAIL_VERIFICATION);
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        user.setEmailVerifiedAt(Instant.now());
        user.setStatus(UserStatus.ACTIVE);
        userRepo.save(user);
    }

    /**
     * Re-sends the email-verification link for an unverified account. Silently succeeds
     * when the email is not found or already verified, preventing account enumeration.
     */
    @Transactional
    public void resendVerification(String email) {
        userRepo.findByEmail(email).ifPresent(user -> {
            if (user.getEmailVerifiedAt() != null) return;
            String token = tokenService.createToken(
                user.getId(), TokenType.EMAIL_VERIFICATION,
                props.getJwt().getEmailVerificationTtl());
            emailService.sendEmailVerification(user.getEmail(), token);
        });
        // Silent if email not found — prevents account enumeration
    }

    /**
     * Initiates a password-reset flow by sending a one-time reset link to the given email.
     * Rate-limited to one request per email per minute and always returns silently — both
     * the rate-limit short-circuit and the unknown-email case produce no observable response,
     * preventing enumeration attacks.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        // Rate-limit: one request per email per minute.
        // Always returns 204 regardless — prevents user enumeration.
        Instant now = Instant.now();
        Instant last = passwordResetLastRequest.get(email);
        if (last != null && now.isBefore(last.plus(PASSWORD_RESET_RATE_LIMIT))) {
            return;
        }
        passwordResetLastRequest.put(email, now);

        userRepo.findByEmail(email).ifPresent(user -> {
            String token = tokenService.createToken(
                user.getId(), TokenType.PASSWORD_RESET,
                props.getJwt().getPasswordResetTtl());
            emailService.sendPasswordReset(user.getEmail(), token);
        });
        // Silent if email not found — prevents user enumeration
    }

    /**
     * Completes a password-reset flow by consuming the one-time token and replacing the
     * stored password hash. The token is invalidated on first use, so replaying it will fail.
     */
    @Transactional
    public void confirmPasswordReset(String rawToken, PasswordResetConfirmRequest req) {
        UUID userId = tokenService.validateAndConsume(rawToken, TokenType.PASSWORD_RESET);
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        Identity identity = identityRepo.findByUserIdAndProvider(user.getId(), IdentityProvider.CREDENTIALS)
            .orElseThrow(() -> new NotFoundException("IDENTITY_NOT_FOUND", "No password credentials for this account"));
        identity.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        identityRepo.save(identity);
    }

    /**
     * Allows an authenticated user to change their own password by verifying the current
     * password before storing the new hash. Throws {@code UnauthorizedException} if the
     * current password does not match, preventing unauthorized credential changes.
     */
    @Transactional
    public void updatePassword(UUID userId, UpdatePasswordRequest req) {
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        Identity identity = identityRepo.findByUserIdAndProvider(user.getId(), IdentityProvider.CREDENTIALS)
            .orElseThrow(() -> new NotFoundException("IDENTITY_NOT_FOUND", "No password credentials for this account"));
        if (!passwordEncoder.matches(req.currentPassword(), identity.getPasswordHash())) {
            throw new UnauthorizedException("INVALID_CREDENTIALS", "Current password is incorrect");
        }
        identity.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        identityRepo.save(identity);
    }

    private AuthTokenResponse mintTokenPair(User user) {
        List<String> permissions = iamService.loadPermissionsForUser(user.getId());
        String accessToken = jwtService.mintAccessToken(user, permissions);
        String refreshToken = tokenService.createRefreshToken(
            user.getId(), props.getAuth().getRefreshTokenExpiry());
        return new AuthTokenResponse(accessToken, refreshToken);
    }

    /**
     * Checks whether an email address is already registered, for use during pre-validation
     * (e.g., real-time availability feedback in the registration form).
     */
    @Transactional(readOnly = true)
    public boolean emailExists(String email) {
        return userRepo.existsByEmail(email);
    }
}
