package io.k2dv.garden.notification.dto;

import io.k2dv.garden.notification.model.NotificationType;
import jakarta.validation.constraints.NotNull;

import java.util.Map;

public record UpdateNotificationPreferencesRequest(
    @NotNull Map<NotificationType, Boolean> preferences
) {}
