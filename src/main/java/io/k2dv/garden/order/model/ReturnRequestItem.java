package io.k2dv.garden.order.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "checkout", name = "return_request_items")
@Getter
@Setter
public class ReturnRequestItem extends BaseEntity {

    @Column(name = "return_request_id", nullable = false)
    private UUID returnRequestId;

    @Column(name = "order_item_id", nullable = false)
    private UUID orderItemId;

    @Column(nullable = false)
    private int quantity;
}
