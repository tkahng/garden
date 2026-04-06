package io.k2dv.garden.content.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePageRequest(
    @NotBlank String title,
    String handle,
    String body,
    String metaTitle,
    String metaDescription
) {}
