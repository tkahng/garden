package io.k2dv.garden.content.dto;

// handleContains is admin-only; storefront controller binds only titleContains
public record BlogFilterRequest(String titleContains, String handleContains) {}
