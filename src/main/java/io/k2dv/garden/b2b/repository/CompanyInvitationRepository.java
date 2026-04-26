package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.CompanyInvitation;
import io.k2dv.garden.b2b.model.InvitationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyInvitationRepository extends JpaRepository<CompanyInvitation, UUID> {

    Optional<CompanyInvitation> findByToken(UUID token);

    List<CompanyInvitation> findByCompanyIdAndStatus(UUID companyId, InvitationStatus status);

    boolean existsByCompanyIdAndEmailAndStatus(UUID companyId, String email, InvitationStatus status);
}
