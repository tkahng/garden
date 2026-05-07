package io.k2dv.garden.b2b.dto;

import java.math.BigDecimal;

public record PriceTierEntry(int minQty, BigDecimal price) {}
