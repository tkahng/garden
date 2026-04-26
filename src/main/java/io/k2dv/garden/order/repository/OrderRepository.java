package io.k2dv.garden.order.repository;

import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.stats.dto.TimeSeriesPoint;
import io.k2dv.garden.stats.dto.TopCustomerEntry;
import io.k2dv.garden.stats.dto.TopProductEntry;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
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

    long countByUserIdAndStatusIn(UUID userId, Collection<OrderStatus> statuses);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.userId = :userId AND o.status IN :statuses")
    BigDecimal sumSpendByUserId(@Param("userId") UUID userId, @Param("statuses") Collection<OrderStatus> statuses);

    @Query(value = """
        SELECT
            TO_CHAR(DATE_TRUNC('day', o.created_at AT TIME ZONE 'UTC'), 'YYYY-MM-DD') AS date,
            COUNT(o.id)                                                                AS order_count,
            COALESCE(SUM(o.total_amount), 0)                                           AS revenue
        FROM checkout.orders o
        WHERE o.status IN ('PAID','PARTIALLY_FULFILLED','FULFILLED')
          AND o.created_at >= :from
          AND o.created_at <= :to
        GROUP BY DATE_TRUNC('day', o.created_at AT TIME ZONE 'UTC')
        ORDER BY DATE_TRUNC('day', o.created_at AT TIME ZONE 'UTC')
        """, nativeQuery = true)
    List<Object[]> findRevenueTimeSeries(@Param("from") Instant from, @Param("to") Instant to);

    @Query(value = """
        SELECT
            p.id         AS product_id,
            p.title,
            p.handle,
            COUNT(DISTINCT oi.order_id)              AS order_count,
            COALESCE(SUM(oi.unit_price * oi.quantity), 0) AS revenue
        FROM checkout.order_items oi
        JOIN catalog.product_variants pv ON pv.id = oi.variant_id
        JOIN catalog.products p          ON p.id  = pv.product_id
        JOIN checkout.orders o           ON o.id  = oi.order_id
        WHERE o.status IN ('PAID','PARTIALLY_FULFILLED','FULFILLED')
          AND o.created_at >= :from
          AND o.created_at <= :to
          AND p.deleted_at IS NULL
        GROUP BY p.id, p.title, p.handle
        ORDER BY revenue DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopProducts(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);

    @Query(value = """
        SELECT
            u.id         AS user_id,
            u.email,
            u.first_name,
            u.last_name,
            COUNT(o.id)                              AS order_count,
            COALESCE(SUM(o.total_amount), 0)         AS revenue
        FROM checkout.orders o
        JOIN auth.users u ON u.id = o.user_id
        WHERE o.status IN ('PAID','PARTIALLY_FULFILLED','FULFILLED')
          AND o.created_at >= :from
          AND o.created_at <= :to
          AND o.user_id IS NOT NULL
        GROUP BY u.id, u.email, u.first_name, u.last_name
        ORDER BY revenue DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Object[]> findTopCustomers(@Param("from") Instant from, @Param("to") Instant to, @Param("limit") int limit);
}
