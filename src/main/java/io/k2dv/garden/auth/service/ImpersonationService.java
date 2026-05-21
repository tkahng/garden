package io.k2dv.garden.auth.service;

import io.k2dv.garden.auth.dto.ImpersonateResponse;
import io.k2dv.garden.auth.model.ImpersonationToken;
import io.k2dv.garden.auth.repository.ImpersonationTokenRepository;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ImpersonationService {

    private static final Duration IMPERSONATION_TTL = Duration.ofMinutes(30);

    private final ImpersonationTokenRepository tokenRepo;
    private final UserRepository userRepo;
    private final JwtService jwtService;

    @Transactional
    public ImpersonateResponse impersonate(UUID targetUserId, UUID adminUserId) {
        User target = userRepo.findById(targetUserId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        Instant expiresAt = Instant.now().plus(IMPERSONATION_TTL);

        // Mint a short-lived token with impersonation marker claim
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

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new ConflictException("HASH_ERROR", "Failed to hash token");
        }
    }
}
