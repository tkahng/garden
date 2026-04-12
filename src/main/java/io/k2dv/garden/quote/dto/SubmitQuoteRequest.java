package io.k2dv.garden.quote.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SubmitQuoteRequest(
    @NotNull UUID companyId,
    @NotBlank String deliveryAddressLine1,
    String deliveryAddressLine2,
    @NotBlank String deliveryCity,
    String deliveryState,
    @NotBlank String deliveryPostalCode,
    @NotBlank String deliveryCountry,
    String shippingRequirements,
    String customerNotes
) {}
