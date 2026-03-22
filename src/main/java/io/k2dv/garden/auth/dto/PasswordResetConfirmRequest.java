package io.k2dv.garden.auth.dto;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PasswordResetConfirmRequest(@NotBlank @Size(min = 8) String newPassword) {}
