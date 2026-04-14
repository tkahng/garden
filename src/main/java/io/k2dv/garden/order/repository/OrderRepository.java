package io.k2dv.garden.order.repository;

import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    Optional<Order> findByStripeSessionId(String stripeSessionId);

    @Query("SELECT COUNT(o) FROM Order o WHERE o.status = :status AND o.createdAt >= :from AND o.createdAt <= :to")
    long countPaidOrdersBetween(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("status") OrderStatus status
    );

    @Query("SELECT SUM(o.totalAmount) FROM Order o WHERE o.status = :status AND o.createdAt >= :from AND o.createdAt <= :to")
    BigDecimal sumRevenueForPaidOrdersBetween(
        @Param("from") Instant from,
        @Param("to") Instant to,
        @Param("status") OrderStatus status
    );
}
