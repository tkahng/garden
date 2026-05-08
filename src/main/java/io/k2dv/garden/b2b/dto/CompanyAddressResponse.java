package io.k2dv.garden.b2b.dto;

import io.k2dv.garden.b2b.model.CompanyShippingAddress;

import java.time.Instant;
import java.util.UUID;

public record CompanyAddressResponse(
    UUID id,
    UUID companyId,
    String label,
    String firstName,
    String lastName,
    String company,
    String address1,
    String address2,
    String city,
    String province,
    String zip,
    String country,
    boolean isDefault,
    Instant createdAt,
    Instant updatedAt
) {
    public static CompanyAddressResponse from(CompanyShippingAddress a) {
        return new CompanyAddressResponse(
            a.getId(), a.getCompanyId(), a.getLabel(),
            a.getFirstName(), a.getLastName(), a.getCompany(),
            a.getAddress1(), a.getAddress2(), a.getCity(),
            a.getProvince(), a.getZip(), a.getCountry(),
            a.isDefault(), a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
