package io.k2dv.garden.product.dto;

import java.util.UUID;

public record StorefrontProductFilterRequest(
        String titleContains,
        String vendor,
        String productType,
        UUID companyId
) {}
