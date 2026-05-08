package io.k2dv.garden.order.template.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateOrderTemplateRequest(
    @NotBlank @Size(max = 100) String name,
    @NotEmpty @Valid List<OrderTemplateItemInput> items
) {}
