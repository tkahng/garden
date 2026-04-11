package io.k2dv.garden.collection.dto.response;

import java.util.UUID;

public record CollectionSummaryResponse(UUID id, String title, String handle, String featuredImageUrl) {}
