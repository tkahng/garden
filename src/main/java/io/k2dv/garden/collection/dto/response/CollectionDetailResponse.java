package io.k2dv.garden.collection.dto.response;

import java.util.UUID;

public record CollectionDetailResponse(
    UUID id, String title, String handle, String description, String featuredImageUrl,
    String metaTitle, String metaDescription
) {}
