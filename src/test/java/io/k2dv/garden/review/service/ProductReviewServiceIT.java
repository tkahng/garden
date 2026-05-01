package io.k2dv.garden.review.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.ProductStatusRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.review.dto.CreateReviewRequest;
import io.k2dv.garden.review.dto.ReviewResponse;
import io.k2dv.garden.review.dto.ReviewSummaryResponse;
import io.k2dv.garden.review.dto.UpdateReviewStatusRequest;
import io.k2dv.garden.review.model.ReviewStatus;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductReviewServiceIT extends AbstractIntegrationTest {

    @Autowired ProductReviewService reviewService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @Autowired ProductService productService;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private UUID productId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "review-user-" + n + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();

        AdminProductResponse product = productService.create(
                new CreateProductRequest("Rose Bush", null, null, null, null, List.of(), null, null));
        productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        productId = product.id();
    }

    private UUID secondUserId() {
        int n = counter.incrementAndGet();
        String email = "review-user-" + n + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Other", "User"));
        return userRepo.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void createReview_persistsReview() {
        ReviewResponse resp = reviewService.createReview(productId, userId,
                new CreateReviewRequest(5, "Excellent", "Really great plant"));

        assertThat(resp.id()).isNotNull();
        assertThat(resp.productId()).isEqualTo(productId);
        assertThat(resp.userId()).isEqualTo(userId);
        assertThat(resp.rating()).isEqualTo(5);
        assertThat(resp.title()).isEqualTo("Excellent");
        assertThat(resp.status()).isEqualTo(ReviewStatus.PUBLISHED);
        assertThat(resp.reviewerName()).isEqualTo("Test U.");
    }

    @Test
    void createReview_duplicateReview_throwsConflict() {
        reviewService.createReview(productId, userId, new CreateReviewRequest(4, null, null));

        assertThatThrownBy(() ->
                reviewService.createReview(productId, userId, new CreateReviewRequest(5, null, null)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void createReview_draftProduct_throwsNotFound() {
        AdminProductResponse draft = productService.create(
                new CreateProductRequest("Draft Plant", null, null, null, null, List.of(), null, null));

        assertThatThrownBy(() ->
                reviewService.createReview(draft.id(), userId, new CreateReviewRequest(5, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void createReview_unknownProduct_throwsNotFound() {
        assertThatThrownBy(() ->
                reviewService.createReview(UUID.randomUUID(), userId, new CreateReviewRequest(5, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void listReviews_returnsOnlyPublishedReviews() {
        UUID userId2 = secondUserId();
        ReviewResponse r1 = reviewService.createReview(productId, userId,
                new CreateReviewRequest(5, "Great", null));
        ReviewResponse r2 = reviewService.createReview(productId, userId2,
                new CreateReviewRequest(3, "Ok", null));
        reviewService.updateStatus(r2.id(), new UpdateReviewStatusRequest(ReviewStatus.HIDDEN));

        PagedResult<ReviewResponse> page = reviewService.listReviews(productId, PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).id()).isEqualTo(r1.id());
    }

    @Test
    void listReviews_unknownProduct_throwsNotFound() {
        assertThatThrownBy(() ->
                reviewService.listReviews(UUID.randomUUID(), PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getReviewSummary_calculatesAverageAndCount() {
        UUID userId2 = secondUserId();
        reviewService.createReview(productId, userId, new CreateReviewRequest(4, null, null));
        reviewService.createReview(productId, userId2, new CreateReviewRequest(5, null, null));

        ReviewSummaryResponse summary = reviewService.getReviewSummary(productId);

        assertThat(summary.reviewCount()).isEqualTo(2);
        assertThat(summary.averageRating()).isEqualByComparingTo(new BigDecimal("4.5"));
    }

    @Test
    void getReviewSummary_noReviews_returnsNullAverageAndZeroCount() {
        ReviewSummaryResponse summary = reviewService.getReviewSummary(productId);

        assertThat(summary.averageRating()).isNull();
        assertThat(summary.reviewCount()).isEqualTo(0);
    }

    @Test
    void updateStatus_canHidePublishedReview() {
        ReviewResponse created = reviewService.createReview(productId, userId,
                new CreateReviewRequest(5, "Good", null));

        ReviewResponse updated = reviewService.updateStatus(created.id(),
                new UpdateReviewStatusRequest(ReviewStatus.HIDDEN));

        assertThat(updated.status()).isEqualTo(ReviewStatus.HIDDEN);
    }

    @Test
    void updateStatus_notFound_throwsNotFoundException() {
        assertThatThrownBy(() ->
                reviewService.updateStatus(UUID.randomUUID(),
                        new UpdateReviewStatusRequest(ReviewStatus.HIDDEN)))
                .isInstanceOf(NotFoundException.class);
    }
}
