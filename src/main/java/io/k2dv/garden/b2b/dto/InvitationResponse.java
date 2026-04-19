package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.CompanyRole;
import io.k2dv.garden.b2b.model.InvitationStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record InvitationResponse(
    UUID id,
    UUID companyId,
    String companyName,
    String email,
    CompanyRole role,
    BigDecimal spendingLimit,
    UUID token,
    UUID invitedBy,
    InvitationStatus status,
    Instant expiresAt,
    Instant createdAt
) {}
