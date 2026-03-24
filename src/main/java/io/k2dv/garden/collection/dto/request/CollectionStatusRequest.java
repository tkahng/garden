package io.k2dv.garden.collection.dto.request;

import io.k2dv.garden.collection.model.CollectionStatus;
import jakarta.validation.constraints.NotNull;

public record CollectionStatusRequest(@NotNull CollectionStatus status) {}
