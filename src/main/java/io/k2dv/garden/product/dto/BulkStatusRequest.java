package io.k2dv.garden.product.dto;

import io.k2dv.garden.product.model.ProductStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record BulkStatusRequest(
    @NotEmpty List<UUID> ids,
    @NotNull ProductStatus status
) {}
