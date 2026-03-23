package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record CreateProductRequest(
    @NotBlank String title,
    String description,
    String handle,
    String vendor,
    String productType,
    List<String> tags
) {}
