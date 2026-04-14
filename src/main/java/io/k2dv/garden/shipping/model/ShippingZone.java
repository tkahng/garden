package io.k2dv.garden.shipping.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.List;

@Entity
@Table(schema = "shipping", name = "shipping_zones")
@Getter
@Setter
public class ShippingZone extends BaseEntity {

    @Column(nullable = false, length = 128)
    private String name;

    @Column(columnDefinition = "text")
    private String description;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "country_codes", columnDefinition = "text[]")
    private List<String> countryCodes;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "provinces", columnDefinition = "text[]")
    private List<String> provinces;

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
