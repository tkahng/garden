package io.k2dv.garden.collection.dto.request;

import io.k2dv.garden.collection.model.CollectionRuleField;
import io.k2dv.garden.collection.model.CollectionRuleOperator;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateCollectionRuleRequest(
    @NotNull CollectionRuleField field,
    @NotNull CollectionRuleOperator operator,
    @NotBlank String value
) {}
