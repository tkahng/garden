package io.k2dv.garden.auth.service;

import io.k2dv.garden.auth.dto.ImpersonateResponse;
import io.k2dv.garden.auth.model.ImpersonationToken;
import io.k2dv.garden.auth.repository.ImpersonationTokenRepository;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.UnauthorizedException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Enables authorized administrators to act on behalf of customer accounts for support
 * and debugging purposes. Issues short-lived (30-minute) impersonation JWTs that carry
 * an empty permission set and an {@code impersonatedBy} claim for audit purposes. Staff
 * and admin accounts are explicitly excluded from being impersonated to prevent privilege
 * escalation.
 */
@Service
@RequiredArgsConstructor
public class ImpersonationService {

    private static final Duration IMPERSONATION_TTL = Duration.ofMinutes(30);
    private static final Set<String> STAFF_ROLES = Set.of("OWNER", "MANAGER", "STAFF");

    private final ImpersonationTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final JwtService jwtService;

    /**
     * Initiates an impersonation session: verifies that the target is not a staff/admin
     * account, mints an impersonation JWT, and persists an audit record. Returns a response
     * containing the token and its expiry so the caller can relay it to the admin client.
     */
    @Transactional
    public ImpersonateResponse impersonate(UUID targetUserId, UUID adminUserId) {
        User target = userRepo.findById(targetUserId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        // Prevent admins from impersonating other staff/admin accounts
        List<String> targetRoles = userRepo.findRoleNamesByUserId(targetUserId);
        if (targetRoles.stream().anyMatch(STAFF_ROLES::contains)) {
            throw new ForbiddenException("CANNOT_IMPERSONATE_STAFF",
                "Staff and admin accounts cannot be impersonated");
        }

        Instant expiresAt = Instant.now().plus(IMPERSONATION_TTL);

        String token = jwtService.mintImpersonationToken(target, adminUserId, expiresAt);
        String tokenHash = sha256(token);

        ImpersonationToken record = new ImpersonationToken();
        record.setTargetUserId(targetUserId);
        record.setAdminUserId(adminUserId);
        record.setTokenHash(tokenHash);
        record.setExpiresAt(expiresAt);
        tokenRepo.save(record);

        return new ImpersonateResponse(token, targetUserId, target.getEmail(), expiresAt);
    }

    /**
     * Validates that the impersonation token exists in the DB and is not expired.
     * Records {@code usedAt} on the first call for audit purposes.
     * Throws {@link UnauthorizedException} if the token is invalid or expired.
     */
    @Transactional
    public void validateAndRecordUse(String tokenHash) {
        ImpersonationToken record = tokenRepo.findByTokenHash(tokenHash)
            .orElseThrow(() -> new UnauthorizedException("INVALID_IMPERSONATION_TOKEN",
                "Impersonation token not found"));

        if (Instant.now().isAfter(record.getExpiresAt())) {
            throw new UnauthorizedException("IMPERSONATION_TOKEN_EXPIRED",
                "Impersonation token has expired");
        }

        if (record.getUsedAt() == null) {
            record.setUsedAt(Instant.now());
            tokenRepo.save(record);
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }
}
