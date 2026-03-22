package io.k2dv.garden.auth.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "identities",
    uniqueConstraints = @UniqueConstraint(columnNames = {"provider", "account_id"}))
@Getter
@Setter
public class Identity extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IdentityProvider provider;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "access_token")
    private String accessToken;

    @Column(name = "refresh_token")
    private String refreshToken;

    @Column(name = "id_token", length = 2048)
    private String idToken;

    @Column(name = "expires_at")
    private Instant expiresAt;
}
