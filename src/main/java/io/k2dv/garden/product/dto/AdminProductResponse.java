package io.k2dv.garden.product.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import io.k2dv.garden.product.model.ProductStatus;

public record AdminProductResponse(
    UUID id,
    String title,
    String description,
    String handle,
    String vendor,
    String productType,
    ProductStatus status,
    UUID featuredImageId,
    List<AdminVariantResponse> variants,
    List<ProductOptionResponse> options,
    List<ProductImageResponse> images,
    List<String> tags,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {}
