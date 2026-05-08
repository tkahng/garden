package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.PaymentMethod;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvoicePaymentResponse(
    UUID id,
    UUID invoiceId,
    BigDecimal amount,
    PaymentMethod paymentMethod,
    String paymentReference,
    String notes,
    Instant paidAt,
    Instant createdAt
) {}
