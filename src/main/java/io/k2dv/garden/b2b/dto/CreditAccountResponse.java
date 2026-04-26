package io.k2dv.garden.b2b.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CreditAccountResponse(
    UUID id,
    UUID companyId,
    BigDecimal creditLimit,
    BigDecimal outstandingBalance,
    BigDecimal availableCredit,
    int paymentTermsDays,
    String currency,
    Instant createdAt,
    Instant updatedAt
) {}
