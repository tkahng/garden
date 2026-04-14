package io.k2dv.garden.shipping.repository;

import io.k2dv.garden.shipping.model.ShippingZone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface ShippingZoneRepository extends JpaRepository<ShippingZone, UUID>, JpaSpecificationExecutor<ShippingZone> {

    @Query(value = """
        SELECT sz.* FROM shipping.shipping_zones sz
        WHERE sz.is_active = true
          AND (sz.country_codes IS NULL OR :country = ANY(sz.country_codes))
          AND (sz.provinces IS NULL OR :province IS NULL OR :province = ANY(sz.provinces))
        """, nativeQuery = true)
    List<ShippingZone> findMatchingZones(@Param("country") String country,
                                         @Param("province") String province);
}
