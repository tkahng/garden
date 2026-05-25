package io.k2dv.garden.auth.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "auth", name = "impersonation_tokens")
@Getter
@Setter
public class ImpersonationToken {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "target_user_id", nullable = false)
    private UUID targetUserId;

    @Column(name = "admin_user_id", nullable = false)
    private UUID adminUserId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
