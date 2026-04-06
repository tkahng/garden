package io.k2dv.garden.product.dto;

public record StorefrontProductFilterRequest(
        String titleContains,
        String vendor,
        String productType
) {}
