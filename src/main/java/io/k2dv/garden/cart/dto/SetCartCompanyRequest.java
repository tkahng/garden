package io.k2dv.garden.cart.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record SetCartCompanyRequest(
    @NotNull UUID companyId
) {}
