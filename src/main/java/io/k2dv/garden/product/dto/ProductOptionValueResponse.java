package io.k2dv.garden.product.dto;

import java.util.UUID;

public record ProductOptionValueResponse(UUID id, String label, int position) {}
