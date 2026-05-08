package io.k2dv.garden.notification.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.notification.dto.NotificationPreferenceResponse;
import io.k2dv.garden.notification.dto.UpdateNotificationPreferencesRequest;
import io.k2dv.garden.notification.service.NotificationPreferenceService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Notification Preferences", description = "Manage email notification opt-ins")
@RestController
@RequestMapping("/api/v1/account/notification-preferences")
@RequiredArgsConstructor
@Authenticated
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> get(
            @CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse.of(preferenceService.getForUser(user.getId())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<List<NotificationPreferenceResponse>>> update(
            @CurrentUser User user,
            @Valid @RequestBody UpdateNotificationPreferencesRequest req) {
        return ResponseEntity.ok(ApiResponse.of(preferenceService.updateForUser(user.getId(), req)));
    }
}
