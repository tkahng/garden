package io.k2dv.garden.product.dto;

import java.util.UUID;

public record ProductSummaryResponse(UUID id, String title, String handle, String vendor) {}
