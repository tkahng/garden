package io.k2dv.garden.search.dto;

import java.time.Instant;
import java.util.UUID;

public record SearchArticleResult(
    UUID id,
    UUID blogId,
    String blogHandle,
    String title,
    String handle,
    String excerpt,
    Instant publishedAt
) {}
