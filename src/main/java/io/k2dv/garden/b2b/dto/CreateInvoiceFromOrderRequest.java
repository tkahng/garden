package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateInvoiceFromOrderRequest(
    @NotNull UUID companyId,
    @Min(0) int paymentTermsDays
) {}
