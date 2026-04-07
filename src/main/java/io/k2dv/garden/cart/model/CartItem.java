package io.k2dv.garden.cart.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(schema = "checkout", name = "cart_items")
@Getter
@Setter
public class CartItem extends BaseEntity {

    @Column(name = "cart_id", nullable = false)
    private UUID cartId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;
}
