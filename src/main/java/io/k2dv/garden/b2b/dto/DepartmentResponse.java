package io.k2dv.garden.b2b.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record DepartmentResponse(
    UUID id,
    UUID companyId,
    UUID parentId,
    String name,
    List<DepartmentResponse> children,
    Instant createdAt
) {}
