package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateCreditAccountRequest(
    @NotNull UUID companyId,
    @NotNull @Positive BigDecimal creditLimit,
    Integer paymentTermsDays,
    String currency
) {}
