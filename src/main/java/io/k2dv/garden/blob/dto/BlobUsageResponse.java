package io.k2dv.garden.blob.dto;

import java.util.UUID;

public record BlobUsageResponse(
    String entityType,
    UUID entityId
) {}
