package io.k2dv.garden.order.template.model;

import io.k2dv.garden.shared.model.ImmutableBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "checkout", name = "order_template_items")
@Getter
@Setter
public class OrderTemplateItem extends ImmutableBaseEntity {

    @Column(name = "template_id", nullable = false)
    private UUID templateId;

    @Column(name = "variant_id", nullable = false)
    private UUID variantId;

    @Column(nullable = false)
    private int quantity;
}
