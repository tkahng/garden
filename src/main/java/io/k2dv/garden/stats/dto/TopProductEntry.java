package io.k2dv.garden.stats.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record TopProductEntry(
    UUID productId,
    String title,
    String handle,
    long orderCount,
    BigDecimal revenue
) {}
