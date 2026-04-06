package io.k2dv.garden.collection.dto.response;

import java.util.UUID;

public record CollectionProductResponse(
    UUID id, UUID productId, String title, String handle, int position
) {}
