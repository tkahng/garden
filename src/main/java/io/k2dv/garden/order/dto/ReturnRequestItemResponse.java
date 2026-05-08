package io.k2dv.garden.order.dto;

import java.util.UUID;

public record ReturnRequestItemResponse(UUID id, UUID orderItemId, int quantity) {}
