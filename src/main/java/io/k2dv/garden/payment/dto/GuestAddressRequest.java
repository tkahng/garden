package io.k2dv.garden.payment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record GuestAddressRequest(
    @NotBlank @Size(max = 64) String firstName,
    @NotBlank @Size(max = 64) String lastName,
    @NotBlank @Size(max = 255) String address1,
    @Size(max = 255) String address2,
    @NotBlank @Size(max = 128) String city,
    @Size(max = 128) String province,
    @NotBlank @Size(max = 20) String zip,
    @NotBlank @Size(max = 2) String country
) {}
