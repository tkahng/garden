package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.CompanyMembership;
import io.k2dv.garden.b2b.model.CompanyRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyMembershipRepository extends JpaRepository<CompanyMembership, UUID> {

    List<CompanyMembership> findByUserId(UUID userId);

    List<CompanyMembership> findByCompanyId(UUID companyId);

    Optional<CompanyMembership> findByCompanyIdAndUserId(UUID companyId, UUID userId);

    boolean existsByCompanyIdAndUserId(UUID companyId, UUID userId);

    List<CompanyMembership> findByUserIdAndRole(UUID userId, CompanyRole role);

    List<CompanyMembership> findByUserIdAndRoleIn(UUID userId, java.util.Collection<CompanyRole> roles);
}
