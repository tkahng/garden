package io.k2dv.garden.quote.dto;

import io.k2dv.garden.quote.model.QuoteStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record QuoteRequestResponse(
    UUID id,
    UUID userId,
    UUID companyId,
    UUID assignedStaffId,
    QuoteStatus status,
    String deliveryAddressLine1,
    String deliveryAddressLine2,
    String deliveryCity,
    String deliveryState,
    String deliveryPostalCode,
    String deliveryCountry,
    String shippingRequirements,
    String customerNotes,
    String staffNotes,
    Instant expiresAt,
    UUID pdfBlobId,
    UUID orderId,
    UUID approverId,
    Instant approvedAt,
    List<QuoteItemResponse> items,
    Instant createdAt,
    Instant updatedAt
) {}
