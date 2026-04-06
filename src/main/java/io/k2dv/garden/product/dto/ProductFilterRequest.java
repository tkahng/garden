package io.k2dv.garden.product.dto;

import io.k2dv.garden.product.model.ProductStatus;

public record ProductFilterRequest(
        ProductStatus status,
        String titleContains,
        String vendor,
        String productType
) {}
