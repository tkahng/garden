package io.k2dv.garden.collection.dto.response;

import io.k2dv.garden.collection.model.CollectionRuleField;
import io.k2dv.garden.collection.model.CollectionRuleOperator;
import java.time.Instant;
import java.util.UUID;

public record CollectionRuleResponse(
    UUID id, CollectionRuleField field, CollectionRuleOperator operator, String value, Instant createdAt
) {}
