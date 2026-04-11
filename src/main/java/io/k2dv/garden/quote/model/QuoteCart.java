package io.k2dv.garden.quote.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "quote", name = "quote_carts")
@Getter
@Setter
public class QuoteCart extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuoteCartStatus status = QuoteCartStatus.ACTIVE;
}
