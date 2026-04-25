package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByHandleAndDeletedAtIsNull(String handle);
    boolean existsByHandleAndDeletedAtIsNullAndIdNot(String handle, UUID id);
    Optional<Product> findByHandle(String handle);
    List<Product> findAllByStatusAndDeletedAtIsNull(ProductStatus status);

    @Query(value = """
        SELECT p.* FROM catalog.product_product_tags t
        JOIN catalog.products p ON p.id = t.product_id
        WHERE t.tag_id IN (
            SELECT tag_id FROM catalog.product_product_tags WHERE product_id = :productId
        )
        AND t.product_id != :productId
        AND p.status = 'ACTIVE'
        AND p.deleted_at IS NULL
        GROUP BY p.id
        ORDER BY COUNT(t.tag_id) DESC, p.created_at DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<Product> findRelatedByTagOverlap(@Param("productId") UUID productId, @Param("limit") int limit);
}
