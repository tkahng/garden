package io.k2dv.garden.order.repository;

import java.math.BigDecimal;
import java.util.UUID;

public interface UserSpendProjection {
    UUID getUserId();
    BigDecimal getTotalSpend();
}
