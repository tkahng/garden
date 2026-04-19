package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.CreditAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CreditAccountRepository extends JpaRepository<CreditAccount, UUID> {
    Optional<CreditAccount> findByCompanyId(UUID companyId);
    boolean existsByCompanyId(UUID companyId);
}
