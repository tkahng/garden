package io.k2dv.garden.order.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record CreateDraftOrderRequest(
    UUID userId,
    String guestEmail,
    @NotEmpty @Valid List<DraftOrderItemRequest> items,
    String shippingAddress,
    String poNumber,
    UUID companyId,
    String currency
) {}
