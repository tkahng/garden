package io.k2dv.garden.inventory.dto;

import java.time.Instant;
import java.util.UUID;

public record LocationResponse(
    UUID id,
    String name,
    String address,
    boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}
