package io.k2dv.garden.admin.user.dto;

public record UpdateUserRequest(
    String firstName,
    String lastName,
    String phone,
    String email
) {}
