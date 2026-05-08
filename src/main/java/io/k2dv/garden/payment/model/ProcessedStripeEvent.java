package io.k2dv.garden.payment.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(schema = "payment", name = "processed_stripe_events")
@Getter
@Setter
public class ProcessedStripeEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "processed_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ DEFAULT clock_timestamp()")
    private Instant processedAt = Instant.now();
}
