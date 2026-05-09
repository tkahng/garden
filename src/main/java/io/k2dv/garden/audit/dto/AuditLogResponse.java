package io.k2dv.garden.audit.dto;

import java.time.Instant;
import java.util.UUID;

public record AuditLogResponse(
    UUID id,
    UUID actorId,
    String actorEmail,
    String action,
    String entityType,
    String entityId,
    String beforeJson,
    String afterJson,
    Instant createdAt
) {}
