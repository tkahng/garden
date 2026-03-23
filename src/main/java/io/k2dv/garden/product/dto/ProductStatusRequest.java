package io.k2dv.garden.product.dto;

import io.k2dv.garden.product.model.ProductStatus;
import jakarta.validation.constraints.NotNull;

public record ProductStatusRequest(@NotNull ProductStatus status) {}
