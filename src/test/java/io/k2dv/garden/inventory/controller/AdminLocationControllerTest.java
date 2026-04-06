package io.k2dv.garden.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.inventory.dto.CreateLocationRequest;
import io.k2dv.garden.inventory.dto.LocationResponse;
import io.k2dv.garden.inventory.service.LocationService;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminLocationController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminLocationControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean LocationService locationService;

    private LocationResponse stubLocation() {
        return new LocationResponse(UUID.randomUUID(), "Warehouse", "123 St", true,
            Instant.now(), Instant.now());
    }

    @Test
    void createLocation_validRequest_returns201() throws Exception {
        when(locationService.create(any())).thenReturn(stubLocation());

        mvc.perform(post("/api/v1/admin/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateLocationRequest("Warehouse", null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.name").value("Warehouse"));
    }

    @Test
    void createLocation_missingName_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/locations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getLocation_notFound_returns404() throws Exception {
        when(locationService.get(any()))
            .thenThrow(new NotFoundException("LOCATION_NOT_FOUND", "Location not found"));

        mvc.perform(get("/api/v1/admin/locations/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("LOCATION_NOT_FOUND"));
    }

    @Test
    void deactivateLocation_returns204() throws Exception {
        doNothing().when(locationService).deactivate(any());

        mvc.perform(delete("/api/v1/admin/locations/{id}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void reactivateLocation_returns200() throws Exception {
        when(locationService.reactivate(any())).thenReturn(stubLocation());

        mvc.perform(post("/api/v1/admin/locations/{id}/reactivate", UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.name").value("Warehouse"));
    }
}
