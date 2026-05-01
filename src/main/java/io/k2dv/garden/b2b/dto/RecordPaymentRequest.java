package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;

public record RecordPaymentRequest(
    @NotNull @Positive BigDecimal amount,
    PaymentMethod paymentMethod,
    String paymentReference,
    String notes,
    Instant paidAt
) {}
