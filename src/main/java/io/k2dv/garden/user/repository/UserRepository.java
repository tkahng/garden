package io.k2dv.garden.user.repository;

import io.k2dv.garden.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @Query("SELECT r.name FROM User u JOIN u.roles r WHERE u.id = :userId")
    List<String> findRoleNamesByUserId(@Param("userId") UUID userId);

    @Query("SELECT DISTINCT p.name FROM User u JOIN u.roles r JOIN r.permissions p WHERE u.id = :userId")
    List<String> findPermissionNamesByUserId(@Param("userId") UUID userId);

    @Query("SELECT COUNT(u) FROM User u WHERE u.createdAt >= :from AND u.createdAt <= :to")
    long countUsersCreatedBetween(@Param("from") Instant from, @Param("to") Instant to);
}
