package io.k2dv.garden.quote.dto;

import io.k2dv.garden.quote.model.QuoteStatus;

import java.util.UUID;

public record QuoteFilter(
    QuoteStatus status,
    UUID companyId,
    UUID assignedStaffId,
    UUID userId
) {}
