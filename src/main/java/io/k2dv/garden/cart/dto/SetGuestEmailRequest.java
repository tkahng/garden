package io.k2dv.garden.cart.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record SetGuestEmailRequest(@NotBlank @Email String email) {}
