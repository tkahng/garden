package io.k2dv.garden.product.dto;

import java.util.UUID;

public record ProductImageResponse(UUID id, String url, String altText, int position) {}
