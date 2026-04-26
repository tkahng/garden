package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.CompanyRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public record CreateInvitationRequest(
    @NotBlank @Email String email,
    CompanyRole role,
    @Positive BigDecimal spendingLimit
) {}
