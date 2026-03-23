package io.k2dv.garden.product.dto;

import java.util.List;
import java.util.UUID;

public record UpdateProductRequest(
    String title,
    String description,
    String handle,
    String vendor,
    String productType,
    UUID featuredImageId,
    List<String> tags
) {}
