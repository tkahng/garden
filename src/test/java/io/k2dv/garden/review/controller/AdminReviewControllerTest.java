package io.k2dv.garden.review.controller;

import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.review.dto.ReviewResponse;
import io.k2dv.garden.review.model.ReviewStatus;
import io.k2dv.garden.review.service.ProductReviewService;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminReviewController.class)
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
class AdminReviewControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProductReviewService reviewService;

    private ReviewResponse stubReview(UUID id) {
        return new ReviewResponse(id, UUID.randomUUID(), UUID.randomUUID(), "Test U.",
                5, "Great", "Really good", false, ReviewStatus.HIDDEN, Instant.now());
    }

    @Test
    void updateStatus_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(reviewService.updateStatus(eq(id), any())).thenReturn(stubReview(id));

        mvc.perform(patch("/api/v1/admin/reviews/{id}/status", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void updateStatus_missingStatus_returns400() throws Exception {
        mvc.perform(patch("/api/v1/admin/reviews/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateStatus_notFound_returns404() throws Exception {
        when(reviewService.updateStatus(any(), any()))
                .thenThrow(new NotFoundException("REVIEW_NOT_FOUND", "Not found"));

        mvc.perform(patch("/api/v1/admin/reviews/{id}/status", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"HIDDEN\"}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("REVIEW_NOT_FOUND"));
    }
}
