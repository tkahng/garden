package io.k2dv.garden.content.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ArticleResponse(
    UUID id, UUID blogId, String title, String handle, String body, String excerpt,
    String authorName, UUID featuredImageId, List<ArticleImageResponse> images,
    List<String> tags, String metaTitle, String metaDescription, Instant publishedAt
) {}
