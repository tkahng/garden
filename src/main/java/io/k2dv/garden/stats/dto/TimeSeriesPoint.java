package io.k2dv.garden.stats.dto;

import java.math.BigDecimal;

public record TimeSeriesPoint(
    String date,
    long orderCount,
    BigDecimal revenue
) {}
