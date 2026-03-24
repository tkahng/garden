package io.k2dv.garden.collection.dto.request;

import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;

public record CollectionFilterRequest(
    CollectionType collectionType, CollectionStatus status, String titleContains
) {}
