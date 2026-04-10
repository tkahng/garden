package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ProductImageRepository extends JpaRepository<ProductImage, UUID> {
    List<ProductImage> findByProductIdOrderByPositionAsc(UUID productId);
    List<ProductImage> findByProductIdInOrderByPositionAsc(Collection<UUID> productIds);
    int countByProductId(UUID productId);
}
