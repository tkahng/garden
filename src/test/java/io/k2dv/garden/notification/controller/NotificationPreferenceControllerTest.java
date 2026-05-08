package io.k2dv.garden.notification.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.notification.dto.NotificationPreferenceResponse;
import io.k2dv.garden.notification.dto.UpdateNotificationPreferencesRequest;
import io.k2dv.garden.notification.model.NotificationType;
import io.k2dv.garden.notification.service.NotificationPreferenceService;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static io.k2dv.garden.config.TestCurrentUserConfig.STUB_USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = NotificationPreferenceController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class, TestCurrentUserConfig.class})
class NotificationPreferenceControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    @MockitoBean NotificationPreferenceService preferenceService;

    private List<NotificationPreferenceResponse> allEnabled() {
        return Arrays.stream(NotificationType.values())
            .map(t -> new NotificationPreferenceResponse(t, true))
            .toList();
    }

    @Test
    void get_returnsAllPreferencesWithDefaults() throws Exception {
        when(preferenceService.getForUser(STUB_USER_ID)).thenReturn(allEnabled());

        mvc.perform(get("/api/v1/account/notification-preferences"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray())
            .andExpect(jsonPath("$.data.length()").value(NotificationType.values().length));
    }

    @Test
    void put_updatesPreferences() throws Exception {
        List<NotificationPreferenceResponse> updated = List.of(
            new NotificationPreferenceResponse(NotificationType.ORDER_CONFIRMATION, true),
            new NotificationPreferenceResponse(NotificationType.MARKETING, false)
        );
        when(preferenceService.updateForUser(eq(STUB_USER_ID), any())).thenReturn(updated);

        mvc.perform(put("/api/v1/account/notification-preferences")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    new UpdateNotificationPreferencesRequest(
                        Map.of(NotificationType.ORDER_CONFIRMATION, true,
                               NotificationType.MARKETING, false)))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data").isArray());
    }
}
