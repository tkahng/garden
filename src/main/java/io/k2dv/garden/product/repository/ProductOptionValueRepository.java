package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.ProductOptionValue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProductOptionValueRepository extends JpaRepository<ProductOptionValue, UUID> {
    List<ProductOptionValue> findByOptionIdOrderByPositionAsc(UUID optionId);
}
