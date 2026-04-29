package io.k2dv.garden.webhook.dto;

import org.hibernate.validator.constraints.URL;

import java.util.List;

public record UpdateWebhookEndpointRequest(
    @URL String url,
    String secret,
    String description,
    List<String> events,
    Boolean active
) {}
