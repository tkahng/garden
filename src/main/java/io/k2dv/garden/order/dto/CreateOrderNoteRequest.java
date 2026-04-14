package io.k2dv.garden.order.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateOrderNoteRequest(@NotBlank String message) {}
