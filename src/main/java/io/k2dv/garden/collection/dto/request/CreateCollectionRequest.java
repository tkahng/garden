package io.k2dv.garden.collection.dto.request;

import io.k2dv.garden.collection.model.CollectionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateCollectionRequest(
    @NotBlank String title,
    String handle,
    String description,
    @NotNull CollectionType collectionType,
    boolean disjunctive,
    UUID featuredImageId
) {}
