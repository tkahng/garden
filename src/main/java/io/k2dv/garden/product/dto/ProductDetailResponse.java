package io.k2dv.garden.product.dto;

import java.util.List;
import java.util.UUID;

public record ProductDetailResponse(
    UUID id,
    String title,
    String description,
    String handle,
    String vendor,
    String productType,
    List<ProductVariantResponse> variants,
    List<ProductImageResponse> images,
    List<String> tags
) {}
