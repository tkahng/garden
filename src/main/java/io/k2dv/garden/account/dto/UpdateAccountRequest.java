package io.k2dv.garden.account.dto;

public record UpdateAccountRequest(
    String firstName,
    String lastName,
    String phone
) {}
