package io.k2dv.garden.order.dto;

import io.k2dv.garden.order.model.ReturnReason;
import io.k2dv.garden.order.model.ReturnResolution;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record SubmitReturnRequest(
    @NotNull ReturnReason reason,
    String notes,
    ReturnResolution resolution,
    List<ReturnRequestItemInput> items
) {}
