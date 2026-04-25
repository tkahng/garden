package io.k2dv.garden.stats.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopCustomerEntry(
    UUID userId,
    String email,
    String firstName,
    String lastName,
    long orderCount,
    BigDecimal revenue
) {}
