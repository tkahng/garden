package io.k2dv.garden.collection.dto.request;

import java.util.UUID;

public record UpdateCollectionRequest(
    String title, String handle, String description, Boolean disjunctive, UUID featuredImageId
) {}
