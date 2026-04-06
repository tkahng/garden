package io.k2dv.garden.product.repository;

import io.k2dv.garden.product.model.ProductTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ProductTagRepository extends JpaRepository<ProductTag, UUID> {
    Optional<ProductTag> findByName(String name);
}
