package io.k2dv.garden.shared.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.util.UUID;

/**
 * Base entity for immutable ledger rows (no updated_at).
 * Use this instead of BaseEntity when the table has no updated_at column.
 */
@MappedSuperclass
@Getter
public abstract class ImmutableBaseEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
