package io.k2dv.garden.notification.dto;

import io.k2dv.garden.notification.model.NotificationType;

public record NotificationPreferenceResponse(NotificationType type, boolean enabled) {}
