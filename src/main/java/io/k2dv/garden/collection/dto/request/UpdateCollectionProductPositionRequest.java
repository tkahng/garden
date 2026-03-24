package io.k2dv.garden.collection.dto.request;

import jakarta.validation.constraints.NotNull;

public record UpdateCollectionProductPositionRequest(@NotNull Integer position) {}
