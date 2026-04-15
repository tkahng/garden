package io.k2dv.garden.fulfillment.repository;

import io.k2dv.garden.fulfillment.model.FulfillmentItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface FulfillmentItemRepository extends JpaRepository<FulfillmentItem, UUID> {

    List<FulfillmentItem> findByFulfillmentId(UUID fulfillmentId);

    @Query("""
        SELECT fi FROM FulfillmentItem fi
        JOIN Fulfillment f ON f.id = fi.fulfillmentId
        WHERE f.orderId = :orderId
          AND f.status IN (io.k2dv.garden.fulfillment.model.FulfillmentStatus.PENDING,
                           io.k2dv.garden.fulfillment.model.FulfillmentStatus.SHIPPED,
                           io.k2dv.garden.fulfillment.model.FulfillmentStatus.DELIVERED)
        """)
    List<FulfillmentItem> findActiveItemsByOrderId(@Param("orderId") UUID orderId);
}
