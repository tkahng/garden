package io.k2dv.garden.quote.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AssignStaffRequest(
    @NotNull UUID staffUserId
) {}
