package io.k2dv.garden.order.repository;

import io.k2dv.garden.order.model.ReturnRequest;
import io.k2dv.garden.order.model.ReturnRequestStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {

    List<ReturnRequest> findByOrderId(UUID orderId);

    Page<ReturnRequest> findByUserId(UUID userId, Pageable pageable);

    Page<ReturnRequest> findByStatus(ReturnRequestStatus status, Pageable pageable);

    boolean existsByOrderIdAndStatusIn(UUID orderId, List<ReturnRequestStatus> statuses);
}
