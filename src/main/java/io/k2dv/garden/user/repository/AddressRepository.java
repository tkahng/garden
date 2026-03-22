package io.k2dv.garden.user.repository;

import io.k2dv.garden.user.model.Address;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AddressRepository extends JpaRepository<Address, UUID> {
    List<Address> findByUserId(UUID userId);
    Optional<Address> findByUserIdAndIsDefaultTrue(UUID userId);
}
