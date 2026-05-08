package io.k2dv.garden.notification.repository;

import io.k2dv.garden.notification.model.NotificationPreference;
import io.k2dv.garden.notification.model.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, UUID> {
    List<NotificationPreference> findByUserId(UUID userId);
    Optional<NotificationPreference> findByUserIdAndNotificationType(UUID userId, NotificationType type);
}
