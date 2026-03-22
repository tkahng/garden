package io.k2dv.garden.iam.repository;

import io.k2dv.garden.iam.model.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface PermissionRepository extends JpaRepository<Permission, UUID> {
    @Query("SELECT p.name FROM Permission p")
    List<String> findAllNames();
}
