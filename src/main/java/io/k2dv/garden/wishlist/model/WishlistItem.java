package io.k2dv.garden.wishlist.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "wishlist_items")
@Getter
@Setter
public class WishlistItem extends BaseEntity {

    @Column(name = "wishlist_id", nullable = false)
    private UUID wishlistId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;
}
