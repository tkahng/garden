package io.k2dv.garden.shared.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record BulkIdsRequest(@NotEmpty List<UUID> ids) {}
