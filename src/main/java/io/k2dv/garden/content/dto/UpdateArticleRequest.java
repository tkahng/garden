package io.k2dv.garden.content.dto;

import java.util.List;
import java.util.UUID;

public record UpdateArticleRequest(
    String title,
    String handle,
    String body,
    String excerpt,
    UUID authorId,
    String metaTitle,
    String metaDescription,
    List<String> tags
) {}
