package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.CompanyShippingAddress;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyShippingAddressRepository extends JpaRepository<CompanyShippingAddress, UUID> {

    List<CompanyShippingAddress> findByCompanyIdOrderByIsDefaultDescCreatedAtAsc(UUID companyId);

    Optional<CompanyShippingAddress> findByIdAndCompanyId(UUID id, UUID companyId);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE CompanyShippingAddress a SET a.isDefault = false WHERE a.companyId = :companyId")
    void clearDefaultForCompany(@Param("companyId") UUID companyId);
}
