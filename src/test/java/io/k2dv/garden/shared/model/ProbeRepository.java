package io.k2dv.garden.shared.model;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProbeRepository extends JpaRepository<ProbeEntity, UUID> {
}
