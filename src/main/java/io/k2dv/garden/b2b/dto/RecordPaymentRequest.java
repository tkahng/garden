package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record RecordPaymentRequest(
    @NotNull @Positive BigDecimal amount,
    String paymentReference,
    String notes,
    Instant paidAt
) {}
