package io.k2dv.garden.content.dto;

import java.time.Instant;
import java.util.UUID;

public record BlogResponse(UUID id, String title, String handle, Instant createdAt) {}
