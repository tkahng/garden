package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record AddMemberRequest(
    @NotBlank @Email String email,
    @Positive BigDecimal spendingLimit
) {}
