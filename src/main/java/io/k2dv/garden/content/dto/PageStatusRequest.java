package io.k2dv.garden.content.dto;

import io.k2dv.garden.content.model.PageStatus;
import jakarta.validation.constraints.NotNull;

public record PageStatusRequest(@NotNull PageStatus status) {}
