package io.k2dv.garden.order.repository;

import io.k2dv.garden.order.model.OrderEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderEventRepository extends JpaRepository<OrderEvent, UUID> {
    List<OrderEvent> findByOrderIdOrderByCreatedAtAsc(UUID orderId);
}
