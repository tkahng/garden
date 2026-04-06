package io.k2dv.garden.collection.dto.response;

import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;
import java.time.Instant;
import java.util.UUID;

public record AdminCollectionSummaryResponse(
    UUID id, String title, String handle,
    CollectionType collectionType, CollectionStatus status,
    long productCount, Instant createdAt
) {}
