package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.NotBlank;

public record CompanyAddressRequest(
    String label,
    @NotBlank String firstName,
    @NotBlank String lastName,
    String company,
    @NotBlank String address1,
    String address2,
    @NotBlank String city,
    String province,
    @NotBlank String zip,
    @NotBlank String country,
    boolean isDefault
) {}
