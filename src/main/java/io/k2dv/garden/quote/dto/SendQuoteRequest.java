package io.k2dv.garden.quote.dto;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record SendQuoteRequest(
    @NotNull @Future Instant expiresAt
) {}
