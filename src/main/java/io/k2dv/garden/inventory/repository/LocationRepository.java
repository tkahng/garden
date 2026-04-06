package io.k2dv.garden.inventory.repository;

import io.k2dv.garden.inventory.model.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LocationRepository extends JpaRepository<Location, UUID> {
}
