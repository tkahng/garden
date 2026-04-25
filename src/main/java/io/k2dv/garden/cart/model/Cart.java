package io.k2dv.garden.cart.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "checkout", name = "carts")
@Getter
@Setter
public class Cart extends BaseEntity {

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "session_id", unique = true)
    private UUID sessionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CartStatus status = CartStatus.ACTIVE;
}
