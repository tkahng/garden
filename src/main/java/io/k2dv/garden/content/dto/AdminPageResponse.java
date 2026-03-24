package io.k2dv.garden.content.dto;

import io.k2dv.garden.content.model.PageStatus;
import java.time.Instant;
import java.util.UUID;

public record AdminPageResponse(
    UUID id, String title, String handle, String body,
    PageStatus status, String metaTitle, String metaDescription,
    Instant publishedAt, Instant createdAt, Instant updatedAt, Instant deletedAt
) {}
