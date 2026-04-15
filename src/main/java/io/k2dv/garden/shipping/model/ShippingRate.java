package io.k2dv.garden.shipping.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(schema = "shipping", name = "shipping_rates")
@Getter
@Setter
public class ShippingRate extends BaseEntity {

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal price;

    @Column(name = "min_weight_grams")
    private Integer minWeightGrams;

    @Column(name = "max_weight_grams")
    private Integer maxWeightGrams;

    @Column(name = "min_order_amount", precision = 19, scale = 4)
    private BigDecimal minOrderAmount;

    @Column(name = "estimated_days_min")
    private Integer estimatedDaysMin;

    @Column(name = "estimated_days_max")
    private Integer estimatedDaysMax;

    @Column(length = 64)
    private String carrier;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
