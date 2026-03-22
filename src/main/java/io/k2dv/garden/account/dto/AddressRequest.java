package io.k2dv.garden.account.dto;

import jakarta.validation.constraints.NotBlank;

public record AddressRequest(
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
