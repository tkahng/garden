package io.k2dv.garden.content.dto;

import io.k2dv.garden.content.model.ArticleStatus;
import java.util.UUID;

public record ArticleFilterRequest(
    ArticleStatus status,
    String titleContains,
    String handleContains,
    UUID authorId,
    String tag,
    String q
) {}
