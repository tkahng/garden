package io.k2dv.garden.inventory.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.UUID;

public record LocationResponse(
    UUID id,
    String name,
    String address,
    @JsonProperty("isActive") boolean isActive,
    Instant createdAt,
    Instant updatedAt
) {}
