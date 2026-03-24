package io.k2dv.garden.content.dto;

import io.k2dv.garden.content.model.ArticleStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminArticleResponse(
    UUID id, UUID blogId, String title, String handle, String body, String excerpt,
    UUID authorId, String authorName, ArticleStatus status,
    UUID featuredImageId, List<ArticleImageResponse> images, List<String> tags,
    String metaTitle, String metaDescription,
    Instant publishedAt, Instant createdAt, Instant updatedAt, Instant deletedAt
) {}
