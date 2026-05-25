package io.k2dv.garden.b2b.dto;

import java.time.Instant;
import java.util.UUID;

public record QuoteApprovalPendencyResponse(
    UUID id,
    UUID ruleId,
    String ruleName,
    String requiredRole,
    String action,
    UUID resolvedBy,
    String rejectionReason,
    Instant resolvedAt
) {}
