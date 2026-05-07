package io.k2dv.garden.b2b.dto;

import java.util.List;
import java.util.UUID;

public record VariantPriceTiersResponse(UUID variantId, List<PriceTierEntry> tiers) {}
