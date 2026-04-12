package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record AddMemberRequest(
    @NotBlank @Email String email
) {}
