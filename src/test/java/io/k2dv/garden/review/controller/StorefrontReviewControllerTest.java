package io.k2dv.garden.review.controller;

import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.review.dto.ReviewResponse;
import io.k2dv.garden.review.model.ReviewStatus;
import io.k2dv.garden.review.service.ProductReviewService;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
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
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorefrontReviewController.class)
@Import({ TestSecurityConfig.class, TestCurrentUserConfig.class, GlobalExceptionHandler.class })
class StorefrontReviewControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean ProductReviewService reviewService;

    private ReviewResponse stubReview(UUID productId) {
        return new ReviewResponse(UUID.randomUUID(), productId, UUID.randomUUID(), "Test U.",
                5, "Great", "Loved it", false, ReviewStatus.PUBLISHED, Instant.now());
    }

    @Test
    void list_returns200() throws Exception {
        UUID productId = UUID.randomUUID();
        var result = new PagedResult<>(List.of(stubReview(productId)),
                PageMeta.builder().page(0).pageSize(10).total(1L).build());
        when(reviewService.listReviews(any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/products/{productId}/reviews", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content.length()").value(1));
    }

    @Test
    void list_productNotFound_returns404() throws Exception {
        when(reviewService.listReviews(any(), any()))
                .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Not found"));

        mvc.perform(get("/api/v1/products/{productId}/reviews", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void create_returns201() throws Exception {
        UUID productId = UUID.randomUUID();
        when(reviewService.createReview(any(), any(), any())).thenReturn(stubReview(productId));

        mvc.perform(post("/api/v1/products/{productId}/reviews", productId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":5,\"title\":\"Great\",\"body\":\"Loved it\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.rating").value(5));
    }

    @Test
    void create_ratingOutOfRange_returns400() throws Exception {
        mvc.perform(post("/api/v1/products/{productId}/reviews", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":6}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_duplicateReview_returns409() throws Exception {
        when(reviewService.createReview(any(), any(), any()))
                .thenThrow(new ConflictException("REVIEW_ALREADY_EXISTS", "Already reviewed"));

        mvc.perform(post("/api/v1/products/{productId}/reviews", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\":4}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("REVIEW_ALREADY_EXISTS"));
    }
}
