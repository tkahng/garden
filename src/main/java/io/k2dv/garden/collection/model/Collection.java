package io.k2dv.garden.collection.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "collections")
@Getter
@Setter
public class Collection extends BaseEntity {
    @Column(nullable = false)
    private String title;
    @Column(nullable = false, unique = true)
    private String handle;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING)
    @Column(name = "collection_type", nullable = false)
    private CollectionType collectionType;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectionStatus status = CollectionStatus.DRAFT;
    @Column(name = "featured_image_id")
    private UUID featuredImageId;
    @Column(nullable = false)
    private boolean disjunctive = false;
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
