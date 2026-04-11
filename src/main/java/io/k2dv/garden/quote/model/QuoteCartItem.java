package io.k2dv.garden.quote.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "quote", name = "quote_cart_items")
@Getter
@Setter
public class QuoteCartItem extends BaseEntity {

    @Column(name = "quote_cart_id", nullable = false)
    private UUID quoteCartId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(nullable = false)
    private int quantity;

    @Column
    private String note;
}
