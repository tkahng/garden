package io.k2dv.garden.newsletter.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SubscribeRequest(
    @NotBlank @Email String email,
    String source
) {}
