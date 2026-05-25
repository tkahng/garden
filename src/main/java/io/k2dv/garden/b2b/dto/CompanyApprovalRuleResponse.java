package io.k2dv.garden.b2b.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record CompanyApprovalRuleResponse(
    UUID id,
    UUID companyId,
    String name,
    BigDecimal thresholdAmount,
    String requiredRole,
    boolean active,
    Instant createdAt
) {}
