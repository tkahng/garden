package io.k2dv.garden.order.template.repository;

import io.k2dv.garden.order.template.model.OrderTemplate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderTemplateRepository extends JpaRepository<OrderTemplate, UUID> {
    List<OrderTemplate> findByUserIdOrderByCreatedAtDesc(UUID userId);
    Optional<OrderTemplate> findByIdAndUserId(UUID id, UUID userId);
}
