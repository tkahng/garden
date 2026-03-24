package io.k2dv.garden.collection.dto.request;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AddCollectionProductRequest(@NotNull UUID productId) {}
