package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceResponse(
    UUID id,
    UUID companyId,
    UUID orderId,
    UUID quoteId,
    InvoiceStatus status,
    BigDecimal totalAmount,
    BigDecimal paidAmount,
    BigDecimal outstandingAmount,
    String currency,
    Instant issuedAt,
    Instant dueAt,
    List<InvoicePaymentResponse> payments,
    Instant createdAt,
    Instant updatedAt
) {}
