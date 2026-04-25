package io.k2dv.garden.review.service;

import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.review.dto.CreateReviewRequest;
import io.k2dv.garden.review.dto.ReviewResponse;
import io.k2dv.garden.review.dto.ReviewSummaryResponse;
import io.k2dv.garden.review.dto.UpdateReviewStatusRequest;
import io.k2dv.garden.review.model.ProductReview;
import io.k2dv.garden.review.model.ReviewStatus;
import io.k2dv.garden.review.repository.ProductReviewRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductReviewService {

    private final ProductReviewRepository reviewRepo;
    private final ProductRepository productRepo;
    private final UserRepository userRepo;

    @Transactional
    public ReviewResponse createReview(UUID productId, UUID userId, CreateReviewRequest req) {
        productRepo.findByIdAndDeletedAtIsNull(productId)
            .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        if (reviewRepo.existsByProductIdAndUserId(productId, userId)) {
            throw new ConflictException("REVIEW_ALREADY_EXISTS", "You have already reviewed this product");
        }

        ProductReview review = new ProductReview();
        review.setProductId(productId);
        review.setUserId(userId);
        review.setRating((short) req.rating().intValue());
        review.setTitle(req.title());
        review.setBody(req.body());

        ProductReview saved = reviewRepo.save(review);
        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        return toResponse(saved, user.getFirstName() + " " + user.getLastName().charAt(0) + ".");
    }

    @Transactional(readOnly = true)
    public PagedResult<ReviewResponse> listReviews(UUID productId, Pageable pageable) {
        productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        Page<ProductReview> page = reviewRepo.findByProductIdAndStatusOrderByCreatedAtDesc(
            productId, ReviewStatus.PUBLISHED, pageable);

        Set<UUID> userIds = page.getContent().stream()
            .map(ProductReview::getUserId)
            .collect(Collectors.toSet());
        Map<UUID, User> usersById = userRepo.findAllById(userIds).stream()
            .collect(Collectors.toMap(User::getId, u -> u));

        return PagedResult.of(page, r -> {
            User user = usersById.get(r.getUserId());
            String name = user != null
                ? user.getFirstName() + " " + user.getLastName().charAt(0) + "."
                : "Anonymous";
            return toResponse(r, name);
        });
    }

    @Transactional(readOnly = true)
    public ReviewSummaryResponse getReviewSummary(UUID productId) {
        Double avg = reviewRepo.findAverageRatingByProductId(productId);
        long count = reviewRepo.countByProductIdAndStatusPublished(productId);
        BigDecimal averageRating = avg != null
            ? BigDecimal.valueOf(avg).setScale(1, RoundingMode.HALF_UP)
            : null;
        return new ReviewSummaryResponse(averageRating, count);
    }

    @Transactional
    public ReviewResponse updateStatus(UUID reviewId, UpdateReviewStatusRequest req) {
        ProductReview review = reviewRepo.findById(reviewId)
            .orElseThrow(() -> new NotFoundException("REVIEW_NOT_FOUND", "Review not found"));
        review.setStatus(req.status());
        ProductReview saved = reviewRepo.save(review);
        User user = userRepo.findById(saved.getUserId())
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));
        return toResponse(saved, user.getFirstName() + " " + user.getLastName().charAt(0) + ".");
    }

    private ReviewResponse toResponse(ProductReview r, String reviewerName) {
        return new ReviewResponse(
            r.getId(),
            r.getProductId(),
            r.getUserId(),
            reviewerName,
            r.getRating(),
            r.getTitle(),
            r.getBody(),
            r.isVerifiedPurchase(),
            r.getStatus(),
            r.getCreatedAt()
        );
    }
}
