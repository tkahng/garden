package io.k2dv.garden.admin.user.dto;

import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AdminUserResponse(
    UUID id,
    String email,
    String firstName,
    String lastName,
    String phone,
    UserStatus status,
    Instant emailVerifiedAt,
    Instant createdAt,
    List<String> roles,
    String adminNotes,
    List<String> tags
) {
    public static AdminUserResponse from(User user, List<String> roles) {
        return new AdminUserResponse(
            user.getId(),
            user.getEmail(),
            user.getFirstName(),
            user.getLastName(),
            user.getPhone(),
            user.getStatus(),
            user.getEmailVerifiedAt(),
            user.getCreatedAt(),
            roles,
            user.getAdminNotes(),
            user.getTags()
        );
    }
}
