package io.k2dv.garden.auth.service;

import io.k2dv.garden.auth.model.Token;
import io.k2dv.garden.auth.model.TokenType;
import io.k2dv.garden.auth.repository.TokenRepository;
import io.k2dv.garden.shared.exception.UnauthorizedException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenService {

    private final TokenRepository tokenRepo;

    @Transactional
    public String createToken(UUID userId, TokenType type, Duration ttl) {
        // Delete any prior token of the same type for this user (rotation)
        tokenRepo.deleteByUserIdAndType(userId, type);

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
