package io.k2dv.garden.content.dto;

import io.k2dv.garden.content.model.ArticleStatus;
import jakarta.validation.constraints.NotNull;

public record ArticleStatusRequest(@NotNull ArticleStatus status) {}
