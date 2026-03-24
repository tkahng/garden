package io.k2dv.garden.content.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateBlogRequest(@NotBlank String title, String handle) {}
