package io.k2dv.garden.fulfillment.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(schema = "checkout", name = "fulfillments")
@Getter
@Setter
public class Fulfillment extends BaseEntity {

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FulfillmentStatus status = FulfillmentStatus.PENDING;

    @Column(name = "tracking_number", length = 128)
    private String trackingNumber;

    @Column(name = "tracking_company", length = 64)
    private String trackingCompany;

    @Column(name = "tracking_url")
    private String trackingUrl;

    @Column
    private String note;
}
