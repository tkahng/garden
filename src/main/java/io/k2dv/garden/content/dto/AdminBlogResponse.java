package io.k2dv.garden.content.dto;

import java.time.Instant;
import java.util.UUID;

public record AdminBlogResponse(UUID id, String title, String handle, Instant createdAt, Instant updatedAt) {}
