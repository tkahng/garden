package io.k2dv.garden.order.template.repository;

import io.k2dv.garden.order.template.model.OrderTemplateItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderTemplateItemRepository extends JpaRepository<OrderTemplateItem, UUID> {
    List<OrderTemplateItem> findByTemplateId(UUID templateId);
    void deleteByTemplateId(UUID templateId);
}
