package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateCompanyRequest(
    @NotBlank String name,
    String taxId,
    String phone,
    String billingAddressLine1,
    String billingAddressLine2,
    String billingCity,
    String billingState,
    String billingPostalCode,
    String billingCountry
) {}
