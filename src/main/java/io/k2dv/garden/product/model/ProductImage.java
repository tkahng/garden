package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "product_images")
@Getter
@Setter
public class ProductImage extends BaseEntity {
    @Column(name = "product_id", nullable = false)
    private UUID productId;
    @Column(name = "blob_id", nullable = false)
    private UUID blobId;
    @Column(name = "alt_text")
    private String altText;
    @Column(nullable = false)
    private int position;
}
