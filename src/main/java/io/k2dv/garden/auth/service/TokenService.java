package io.k2dv.garden.auth.service;

import io.k2dv.garden.auth.model.RefreshToken;
import io.k2dv.garden.auth.model.Token;
import io.k2dv.garden.auth.model.TokenType;
import io.k2dv.garden.auth.repository.RefreshTokenRepository;
import io.k2dv.garden.auth.repository.TokenRepository;
import io.k2dv.garden.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Manages the lifecycle of all non-JWT tokens: single-use one-time tokens (email
 * verification, password reset) stored in the {@code auth.tokens} table, and rotating
 * refresh tokens stored in {@code auth.refresh_tokens}. All tokens are stored as
 * SHA-256 hashes; raw values are never persisted, only returned to callers for
 * delivery to the client.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private final TokenRepository tokenRepo;
    private final RefreshTokenRepository refreshTokenRepo;

    /**
     * Creates and persists a new one-time token of the given type for the user. Any
     * existing token of the same type is deleted first, ensuring only one active
     * token exists per purpose (e.g. a re-sent verification link invalidates the previous one).
     * Returns the raw (unhashed) token value to be delivered out-of-band to the user.
     */
    @Transactional
    public String createToken(UUID userId, TokenType type, Duration ttl) {
        // Verification/reset tokens are single-purpose. Refresh tokens are
        // per-session so logging in elsewhere does not revoke this session.
        if (type != TokenType.REFRESH_TOKEN) {
            tokenRepo.deleteByUserIdAndType(userId, type);
        }

        String raw = UUID.randomUUID().toString();
        String hash = hash(raw);

        Token token = new Token();
        token.setUserId(userId);
        token.setType(type);
        token.setTokenHash(hash);
        token.setExpiresAt(Instant.now().plus(ttl));
        tokenRepo.save(token);

        return raw;
    }

    /**
     * Validates a one-time token against the expected type, deletes it (consuming it
     * for single-use semantics), and returns the owning user's ID. Throws
     * {@code UnauthorizedException} if the token is not found, already consumed, or expired.
     */
    @Transactional
    public UUID validateAndConsume(String rawToken, TokenType type) {
        String hash = hash(rawToken);
        Token token = tokenRepo.findByTokenHashAndType(hash, type)
            .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "Token not found or already used"));

        if (token.getExpiresAt().isBefore(Instant.now())) {
            tokenRepo.delete(token);
            throw new UnauthorizedException("TOKEN_EXPIRED", "Token has expired");
        }

        UUID userId = token.getUserId();
        tokenRepo.delete(token);
        return userId;
    }

    // -------------------------------------------------------------------------
    // Rotating refresh tokens (auth.refresh_tokens table)
    // -------------------------------------------------------------------------

    /**
     * Issue a brand-new refresh token for a user (e.g. on login).
     *
     * @return opaque raw token value to return to the client
     */
    @Transactional
    public String createRefreshToken(UUID userId, Duration ttl) {
        String raw = UUID.randomUUID().toString();
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(hash(raw));
        token.setExpiresAt(Instant.now().plus(ttl));
        refreshTokenRepo.save(token);
        return raw;
    }

    /**
     * Rotate a refresh token: validate, revoke the old one, issue a new one.
     *
     * @return new raw refresh token
     * @throws UnauthorizedException on any invalid/expired/replayed token
     */
    @Transactional
    public RotatedRefreshToken rotateRefreshToken(String rawToken, Duration ttl) {
        String hash = hash(rawToken);
        RefreshToken existing = refreshTokenRepo.findByTokenHash(hash)
            .orElseThrow(() -> new UnauthorizedException("INVALID_TOKEN", "Refresh token not found"));

        if (existing.getRevokedAt() != null) {
            // Token reuse detected — revoke entire family
            int revoked = refreshTokenRepo.revokeAllForUser(existing.getUserId(), Instant.now());
            log.warn("Refresh token reuse detected for user {}; {} token(s) revoked", existing.getUserId(), revoked);
            throw new UnauthorizedException("TOKEN_REUSE", "Token reuse detected; all sessions have been revoked");
        }

        if (existing.getExpiresAt().isBefore(Instant.now())) {
            refreshTokenRepo.delete(existing);
            throw new UnauthorizedException("TOKEN_EXPIRED", "Refresh token has expired");
        }

        // Issue replacement
        String newRaw = UUID.randomUUID().toString();
        RefreshToken replacement = new RefreshToken();
        replacement.setUserId(existing.getUserId());
        replacement.setTokenHash(hash(newRaw));
        replacement.setExpiresAt(Instant.now().plus(ttl));
        refreshTokenRepo.save(replacement);

        // Revoke old token and record what replaced it
        existing.setRevokedAt(Instant.now());
        existing.setReplacedByToken(newRaw);
        refreshTokenRepo.save(existing);

        return new RotatedRefreshToken(existing.getUserId(), newRaw);
    }

    /**
     * Revoke a refresh token on explicit logout.
     * No-op if the token is not found (idempotent).
     */
    @Transactional
    public void revokeRefreshToken(String rawToken) {
        String hash = hash(rawToken);
        refreshTokenRepo.findByTokenHash(hash).ifPresent(t -> {
            if (t.getRevokedAt() == null) {
                t.setRevokedAt(Instant.now());
                refreshTokenRepo.save(t);
            }
        });
    }

    /** Carries the result of a successful token rotation. */
    public record RotatedRefreshToken(UUID userId, String newRawToken) {}

    private String hash(String raw) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
