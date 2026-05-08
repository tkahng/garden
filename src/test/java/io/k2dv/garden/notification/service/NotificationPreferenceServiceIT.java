package io.k2dv.garden.notification.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.notification.dto.NotificationPreferenceResponse;
import io.k2dv.garden.notification.dto.UpdateNotificationPreferencesRequest;
import io.k2dv.garden.notification.model.NotificationType;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationPreferenceServiceIT extends AbstractIntegrationTest {

    @Autowired NotificationPreferenceService preferenceService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);
    private UUID userId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "notifpref-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void getForUser_noPreferencesSaved_returnsAllEnabledByDefault() {
        var prefs = preferenceService.getForUser(userId);

        assertThat(prefs).hasSize(NotificationType.values().length);
        assertThat(prefs).allMatch(NotificationPreferenceResponse::enabled);
    }

    @Test
    void updateForUser_disablesMarketing_reflectedOnGet() {
        preferenceService.updateForUser(userId, new UpdateNotificationPreferencesRequest(
            Map.of(NotificationType.MARKETING, false)));

        var prefs = preferenceService.getForUser(userId);
        assertThat(prefs).anyMatch(p -> p.type() == NotificationType.MARKETING && !p.enabled());
        assertThat(prefs).anyMatch(p -> p.type() == NotificationType.ORDER_CONFIRMATION && p.enabled());
    }

    @Test
    void updateForUser_idempotent_canReEnable() {
        preferenceService.updateForUser(userId, new UpdateNotificationPreferencesRequest(
            Map.of(NotificationType.ORDER_SHIPPED, false)));
        preferenceService.updateForUser(userId, new UpdateNotificationPreferencesRequest(
            Map.of(NotificationType.ORDER_SHIPPED, true)));

        assertThat(preferenceService.isEnabled(userId, NotificationType.ORDER_SHIPPED)).isTrue();
    }

    @Test
    void isEnabled_noRow_returnsTrue() {
        assertThat(preferenceService.isEnabled(userId, NotificationType.ORDER_DELIVERED)).isTrue();
    }

    @Test
    void isEnabled_disabledRow_returnsFalse() {
        preferenceService.updateForUser(userId, new UpdateNotificationPreferencesRequest(
            Map.of(NotificationType.ORDER_CANCELLED, false)));

        assertThat(preferenceService.isEnabled(userId, NotificationType.ORDER_CANCELLED)).isFalse();
    }

    @Test
    void isEnabled_nullUserId_alwaysTrue() {
        assertThat(preferenceService.isEnabled(null, NotificationType.MARKETING)).isTrue();
    }
}
