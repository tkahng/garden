package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID> {
    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByHandleAndDeletedAtIsNull(String handle);
    boolean existsByHandleAndDeletedAtIsNullAndIdNot(String handle, UUID id);
    Optional<Product> findByHandle(String handle);

    @Query("SELECT p FROM Product p WHERE p.deletedAt IS NULL AND (:status IS NULL OR p.status = :status) ORDER BY p.id ASC")
    List<Product> findForAdminList(@Param("status") ProductStatus status, Limit limit);

    @Query("SELECT p FROM Product p WHERE p.deletedAt IS NULL AND (:status IS NULL OR p.status = :status) AND p.id > :cursor ORDER BY p.id ASC")
    List<Product> findForAdminListAfterCursor(@Param("status") ProductStatus status, @Param("cursor") UUID cursor, Limit limit);

    List<Product> findByStatusAndDeletedAtIsNullOrderByIdAsc(ProductStatus status, Limit limit);
    List<Product> findByStatusAndDeletedAtIsNullAndIdGreaterThanOrderByIdAsc(ProductStatus status, UUID cursor, Limit limit);
}
