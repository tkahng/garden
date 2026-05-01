package io.k2dv.garden.shared.dto;

import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record MetadataRequest(@NotNull Map<String, Object> metadata) {}
