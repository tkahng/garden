package io.k2dv.garden.content.dto;

public record UpdatePageRequest(
    String title,
    String handle,
    String body,
    String metaTitle,
    String metaDescription
) {}
