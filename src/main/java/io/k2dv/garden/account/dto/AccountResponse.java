package io.k2dv.garden.account.dto;

import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;

import java.time.Instant;
import java.util.UUID;

public record AccountResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String phone,
    UserStatus status,
    Instant emailVerifiedAt
) {
    public static AccountResponse from(User user) {
        return new AccountResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhone(),
            user.getStatus(),
            user.getEmailVerifiedAt()
        );
    }
}
