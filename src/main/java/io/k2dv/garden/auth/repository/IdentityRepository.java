package io.k2dv.garden.auth.repository;

import io.k2dv.garden.auth.model.Identity;
import io.k2dv.garden.auth.model.IdentityProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdentityRepository extends JpaRepository<Identity, UUID> {
    Optional<Identity> findByProviderAndAccountId(IdentityProvider provider, String accountId);
    Optional<Identity> findByUserIdAndProvider(UUID userId, IdentityProvider provider);
}
