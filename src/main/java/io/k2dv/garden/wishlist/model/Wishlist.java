package io.k2dv.garden.wishlist.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "wishlists")
@Getter
@Setter
public class Wishlist extends BaseEntity {

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;
}
