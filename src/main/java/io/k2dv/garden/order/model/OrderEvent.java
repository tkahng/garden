package io.k2dv.garden.order.model;

import io.k2dv.garden.shared.model.ImmutableBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.Map;
import java.util.UUID;

@Entity
@Table(schema = "checkout", name = "order_events")
@Getter
@Setter
public class OrderEvent extends ImmutableBaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderEventType type;

    @Column
    private String message;

    @Column(name = "author_id")
    private UUID authorId;

    @Column(name = "author_name", length = 128)
    private String authorName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> metadata;
}
