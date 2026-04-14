package io.k2dv.garden.shipping.repository;

import io.k2dv.garden.shipping.model.ShippingRate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShippingRateRepository extends JpaRepository<ShippingRate, UUID> {
    List<ShippingRate> findByZoneId(UUID zoneId);
    Optional<ShippingRate> findByIdAndZoneId(UUID id, UUID zoneId);
    void deleteByZoneId(UUID zoneId);
}
