package io.k2dv.garden.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Persisted refresh token with rotation support.
 *
 * <p>On each use the old token is revoked ({@code revokedAt} set) and a new token
 * is issued. {@code replacedByToken} records the raw successor token so the full
 * rotation chain is auditable.
 *
 * <p>If a previously-revoked token is presented again it means the token was stolen
 * — the entire chain (all tokens for the same user) is revoked immediately.
 */
@Entity
@Table(schema = "auth", name = "refresh_tokens")
@Getter
@Setter
public class RefreshToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** SHA-256 hex of the opaque raw token value. */
    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Set when the token is consumed (rotated) or explicitly revoked. */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    /**
     * Raw value of the replacement token issued when this token was rotated.
     * Stored so the chain can be traced in audit queries.
     */
    @Column(name = "replaced_by_token")
    private String replacedByToken;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
