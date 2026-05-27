package io.k2dv.garden.notification.service;

import io.k2dv.garden.notification.dto.NotificationPreferenceResponse;
import io.k2dv.garden.notification.dto.UpdateNotificationPreferencesRequest;
import io.k2dv.garden.notification.model.NotificationPreference;
import io.k2dv.garden.notification.model.NotificationType;
import io.k2dv.garden.notification.repository.NotificationPreferenceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Manages per-user notification opt-in/opt-out preferences for each {@link NotificationType}.
 * All notification-sending code should call {@link #isEnabled(UUID, NotificationType)} before
 * dispatching a transactional email, respecting any explicit opt-out stored here.
 */
@Service
@RequiredArgsConstructor
public class NotificationPreferenceService {

    private final NotificationPreferenceRepository preferenceRepo;

    /**
     * Returns the full list of notification types with their current enabled state for a user,
     * defaulting to {@code true} (opted-in) for any type that has no explicit preference row.
     */
    @Transactional(readOnly = true)
    public List<NotificationPreferenceResponse> getForUser(UUID userId) {
        Map<NotificationType, Boolean> saved = preferenceRepo.findByUserId(userId).stream()
            .collect(Collectors.toMap(
                NotificationPreference::getNotificationType,
                NotificationPreference::isEnabled));

        return Arrays.stream(NotificationType.values())
            .map(type -> new NotificationPreferenceResponse(type, saved.getOrDefault(type, true)))
            .toList();
    }

    /**
     * Applies a partial update to the user's notification preferences, creating preference rows
     * for types that have no prior setting and updating existing rows in place.
     */
    @Transactional
    public List<NotificationPreferenceResponse> updateForUser(UUID userId,
                                                               UpdateNotificationPreferencesRequest req) {
        Map<NotificationType, NotificationPreference> existing = preferenceRepo.findByUserId(userId).stream()
            .collect(Collectors.toMap(NotificationPreference::getNotificationType, p -> p));

        req.preferences().forEach((type, enabled) -> {
            NotificationPreference pref = existing.computeIfAbsent(type, t -> {
                NotificationPreference np = new NotificationPreference();
                np.setUserId(userId);
                np.setNotificationType(t);
                return np;
            });
            pref.setEnabled(enabled);
            preferenceRepo.save(pref);
        });

        return getForUser(userId);
    }

    /**
     * Returns true if the user has the notification enabled, or if no preference row exists
     * (defaults to opted-in). Guest users (null userId) always receive notifications.
     */
    public boolean isEnabled(UUID userId, NotificationType type) {
        if (userId == null) return true;
        return preferenceRepo.findByUserIdAndNotificationType(userId, type)
            .map(NotificationPreference::isEnabled)
            .orElse(true);
    }
}
