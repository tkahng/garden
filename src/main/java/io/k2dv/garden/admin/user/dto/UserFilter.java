package io.k2dv.garden.admin.user.dto;

import io.k2dv.garden.user.model.UserStatus;

public record UserFilter(UserStatus status, String email) {}
