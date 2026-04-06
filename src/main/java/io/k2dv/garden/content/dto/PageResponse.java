package io.k2dv.garden.content.dto;

import java.time.Instant;
import java.util.UUID;

public record PageResponse(
    UUID id, String title, String handle, String body,
    String metaTitle, String metaDescription, Instant publishedAt
) {}
