package io.k2dv.garden.account.dto;

import io.k2dv.garden.user.model.Address;

import java.util.UUID;

public record AddressResponse(
    UUID id,
    String firstName,
    String lastName,
    String company,
    String address1,
    String address2,
    String city,
    String province,
    String zip,
    String country,
    boolean isDefault
) {
    public static AddressResponse from(Address a) {
        return new AddressResponse(
            a.getId(),
            a.getFirstName(),
            a.getLastName(),
            a.getCompany(),
            a.getAddress1(),
            a.getAddress2(),
            a.getCity(),
            a.getProvince(),
            a.getZip(),
            a.getCountry(),
            a.isDefault()
        );
    }
}
