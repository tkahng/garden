package io.k2dv.garden.product.dto;

import java.util.List;
import java.util.UUID;

public record ProductOptionResponse(UUID id, String name, int position, List<ProductOptionValueResponse> values) {}
