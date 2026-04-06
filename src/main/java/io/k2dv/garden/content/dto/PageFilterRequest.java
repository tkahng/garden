package io.k2dv.garden.content.dto;

import io.k2dv.garden.content.model.PageStatus;

public record PageFilterRequest(PageStatus status, String titleContains, String handleContains, String q) {}
