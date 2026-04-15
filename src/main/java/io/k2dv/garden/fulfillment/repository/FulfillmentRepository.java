package io.k2dv.garden.fulfillment.repository;

import io.k2dv.garden.fulfillment.model.Fulfillment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FulfillmentRepository extends JpaRepository<Fulfillment, UUID> {
    List<Fulfillment> findByOrderId(UUID orderId);
    Optional<Fulfillment> findByIdAndOrderId(UUID id, UUID orderId);
}
