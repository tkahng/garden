package io.k2dv.garden.stats.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record StatsResponse(
    Instant from,
    Instant to,
    long orderCount,
    BigDecimal totalRevenue,
    BigDecimal averageOrderValue,
    long newCustomerCount
) {}
