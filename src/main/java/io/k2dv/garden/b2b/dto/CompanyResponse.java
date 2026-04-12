package io.k2dv.garden.b2b.dto;

import java.time.Instant;
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
    Instant createdAt,
    Instant updatedAt
) {}
