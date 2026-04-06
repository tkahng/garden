package io.k2dv.garden.product.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateImageRequest(@NotNull UUID blobId, String altText) {}
