package io.k2dv.garden.blob.dto;

import java.util.List;
import java.util.UUID;

public record BulkDeleteRequest(List<UUID> ids) {}
