package io.k2dv.garden.b2b.dto;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record CompanyResponse(
    UUID id,
    String name,
    String taxId,
    String phone,
    String billingAddressLine1,
    String billingAddressLine2,
    String billingCity,
    String billingState,
    String billingPostalCode,
    String billingCountry,
    boolean taxExempt,
    UUID salesRepUserId,
    Map<String, Object> metadata,
    Instant createdAt,
    Instant updatedAt
) {}
