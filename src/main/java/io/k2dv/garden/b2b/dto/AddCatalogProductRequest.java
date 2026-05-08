package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record AddCatalogProductRequest(@NotNull UUID productId) {}
