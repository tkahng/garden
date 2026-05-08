package io.k2dv.garden.order.repository;

import io.k2dv.garden.order.model.ReturnRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReturnRequestItemRepository extends JpaRepository<ReturnRequestItem, UUID> {

    List<ReturnRequestItem> findByReturnRequestId(UUID returnRequestId);
}
