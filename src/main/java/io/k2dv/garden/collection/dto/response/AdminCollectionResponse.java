package io.k2dv.garden.collection.dto.response;

import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminCollectionResponse(
    UUID id, String title, String handle, String description,
    CollectionType collectionType, CollectionStatus status,
    UUID featuredImageId, boolean disjunctive, long productCount,
    List<CollectionRuleResponse> rules,
    Instant createdAt, Instant updatedAt, Instant deletedAt
) {}
