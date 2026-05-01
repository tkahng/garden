package io.k2dv.garden.b2b.dto;

import java.util.Map;
import java.util.UUID;

public record AdminUpdateCompanyRequest(
    String name,
    String taxId,
    String phone,
    String billingAddressLine1,
    String billingAddressLine2,
    String billingCity,
    String billingState,
    String billingPostalCode,
    String billingCountry,
    Boolean taxExempt,
    UUID salesRepUserId
) {}
