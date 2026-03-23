package io.k2dv.garden.product.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminProductResponse(
    UUID id,
    String title,
    String description,
    String handle,
    String vendor,
    String productType,
    String status,
    UUID featuredImageId,
    List<AdminVariantResponse> variants,
    List<ProductImageResponse> images,
    List<String> tags,
    Instant createdAt,
    Instant updatedAt,
    Instant deletedAt
) {}
