package io.k2dv.garden.content.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;

public record CreateArticleRequest(
    @NotBlank String title,
    String handle,
    String body,
    String excerpt,
    UUID authorId,
    String metaTitle,
    String metaDescription,
    List<String> tags
) {}
