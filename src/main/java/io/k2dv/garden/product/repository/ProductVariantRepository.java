package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductVariantRepository extends JpaRepository<ProductVariant, UUID> {
    List<ProductVariant> findByProductIdAndDeletedAtIsNullOrderByCreatedAtAsc(UUID productId);
    Optional<ProductVariant> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT v FROM ProductVariant v JOIN v.optionValues ov WHERE ov.id = :optionValueId AND v.deletedAt IS NULL")
    List<ProductVariant> findByOptionValueIdAndDeletedAtIsNull(@Param("optionValueId") UUID optionValueId);
}
