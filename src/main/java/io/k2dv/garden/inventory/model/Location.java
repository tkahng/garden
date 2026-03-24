package io.k2dv.garden.inventory.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "locations")
@Getter
@Setter
public class Location extends BaseEntity {
    @Column(nullable = false)
    private String name;
    private String address;
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}
