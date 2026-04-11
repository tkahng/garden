package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.CompanyRole;

import java.time.Instant;
import java.util.UUID;

public record CompanyMemberResponse(
    UUID membershipId,
    UUID userId,
    String email,
    String firstName,
    String lastName,
    CompanyRole role,
    Instant joinedAt
) {}
