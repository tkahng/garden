package io.k2dv.garden.review.repository;

import io.k2dv.garden.review.model.ProductReview;
import io.k2dv.garden.review.model.ReviewStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface ProductReviewRepository extends JpaRepository<ProductReview, UUID> {

    Page<ProductReview> findByProductIdAndStatusOrderByCreatedAtDesc(UUID productId, ReviewStatus status, Pageable pageable);

    boolean existsByProductIdAndUserId(UUID productId, UUID userId);

    @Query("SELECT AVG(r.rating) FROM ProductReview r WHERE r.productId = :productId AND r.status = 'PUBLISHED'")
    Double findAverageRatingByProductId(@Param("productId") UUID productId);

    @Query("SELECT COUNT(r) FROM ProductReview r WHERE r.productId = :productId AND r.status = 'PUBLISHED'")
    long countByProductIdAndStatusPublished(@Param("productId") UUID productId);
}
