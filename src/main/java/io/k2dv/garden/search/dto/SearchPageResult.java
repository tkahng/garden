package io.k2dv.garden.search.dto;

import java.time.Instant;
import java.util.UUID;

public record SearchPageResult(
    UUID id,
    String title,
    String handle,
    Instant publishedAt
) {}
