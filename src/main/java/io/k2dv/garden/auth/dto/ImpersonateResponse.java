package io.k2dv.garden.auth.dto;

import java.time.Instant;
import java.util.UUID;

public record ImpersonateResponse(
    String accessToken,
    UUID targetUserId,
    String targetEmail,
    Instant expiresAt
) {}
