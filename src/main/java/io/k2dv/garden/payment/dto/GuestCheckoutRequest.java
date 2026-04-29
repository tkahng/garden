package io.k2dv.garden.payment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record GuestCheckoutRequest(
    @NotBlank @Email String email,
    @NotNull @Valid GuestAddressRequest shippingAddress,
    @NotNull UUID shippingRateId,
    String discountCode,
    String giftCardCode,
    String poNumber
) {}
