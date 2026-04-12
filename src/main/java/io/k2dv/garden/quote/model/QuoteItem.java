package io.k2dv.garden.quote.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(schema = "quote", name = "quote_items")
@Getter
@Setter
public class QuoteItem extends BaseEntity {

    @Column(name = "quote_request_id", nullable = false)
    private UUID quoteRequestId;

    @Column(name = "variant_id")
    private UUID variantId;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", precision = 19, scale = 4)
    private BigDecimal unitPrice;
}
