package io.k2dv.garden.order.dto;

import io.k2dv.garden.order.model.OrderStatus;

import java.time.Instant;
import java.util.UUID;

public record OrderFilter(
    OrderStatus status,
    UUID userId,
    Instant from,
    Instant to
) {}
