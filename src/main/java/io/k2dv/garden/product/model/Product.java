package io.k2dv.garden.product.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "products")
@Getter
@Setter
public class Product extends BaseEntity {
    @Column(nullable = false)
    private String title;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Column(nullable = false, unique = true)
    private String handle;
    private String vendor;
    @Column(name = "product_type")
    private String productType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductStatus status = ProductStatus.DRAFT;
    @Column(name = "featured_image_id")
    private UUID featuredImageId;
    @Column(name = "meta_title")
    private String metaTitle;
    @Column(name = "meta_description", columnDefinition = "TEXT")
    private String metaDescription;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        schema = "catalog",
        name = "product_product_tags",
        joinColumns = @JoinColumn(name = "product_id"),
        inverseJoinColumns = @JoinColumn(name = "tag_id")
    )
    private Set<ProductTag> tags = new LinkedHashSet<>();
}
