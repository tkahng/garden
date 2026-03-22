package io.k2dv.garden.auth.repository;

import io.k2dv.garden.auth.model.Token;
import io.k2dv.garden.auth.model.TokenType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TokenRepository extends JpaRepository<Token, UUID> {
    Optional<Token> findByTokenHashAndType(String tokenHash, TokenType type);
    void deleteByUserIdAndType(UUID userId, TokenType type);
}
