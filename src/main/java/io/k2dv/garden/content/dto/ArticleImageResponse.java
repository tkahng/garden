package io.k2dv.garden.content.dto;

import java.util.UUID;

public record ArticleImageResponse(UUID id, String url, String altText, int position) {}
