package io.k2dv.garden.content.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateArticleImageRequest(@NotNull UUID blobId, String altText) {}
