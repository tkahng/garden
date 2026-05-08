package io.k2dv.garden.order.dto;

import io.k2dv.garden.order.model.ReturnReason;
import io.k2dv.garden.order.model.ReturnRequestStatus;
import io.k2dv.garden.order.model.ReturnResolution;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ReturnRequestResponse(
    UUID id,
    UUID orderId,
    UUID userId,
    ReturnReason reason,
    String notes,
    ReturnResolution resolution,
    ReturnRequestStatus status,
    String staffNotes,
    UUID resolvedBy,
    Instant resolvedAt,
    List<ReturnRequestItemResponse> items,
    Instant createdAt,
    Instant updatedAt
) {}
