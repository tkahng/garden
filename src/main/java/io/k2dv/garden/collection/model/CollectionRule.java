package io.k2dv.garden.collection.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.generator.EventType;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(schema = "catalog", name = "collection_rules")
@Getter
@Setter
public class CollectionRule {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;
    @Column(name = "collection_id", nullable = false)
    private UUID collectionId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectionRuleField field;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CollectionRuleOperator operator;
    @Column(nullable = false)
    private String value;
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
