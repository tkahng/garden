package io.k2dv.garden.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.hibernate.validator.constraints.URL;

import java.util.List;

public record CreateWebhookEndpointRequest(
    @NotBlank @URL String url,
    @NotBlank String secret,
    String description,
    @NotNull List<String> events
) {}
