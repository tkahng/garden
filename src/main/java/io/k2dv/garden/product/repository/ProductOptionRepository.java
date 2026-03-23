package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.ProductOption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProductOptionRepository extends JpaRepository<ProductOption, UUID> {
    List<ProductOption> findByProductIdOrderByPositionAsc(UUID productId);
}
