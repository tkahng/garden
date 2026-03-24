package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProductRepository extends JpaRepository<Product, UUID>, JpaSpecificationExecutor<Product> {
    Optional<Product> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByHandleAndDeletedAtIsNull(String handle);
    boolean existsByHandleAndDeletedAtIsNullAndIdNot(String handle, UUID id);
    Optional<Product> findByHandle(String handle);
    List<Product> findAllByStatusAndDeletedAtIsNull(ProductStatus status);
}
