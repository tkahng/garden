package io.k2dv.garden.fulfillment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateFulfillmentRequest(
    String trackingNumber,
    String trackingCompany,
    String trackingUrl,
    String note,
    @NotEmpty @Valid List<FulfillmentItemRequest> items
) {}
