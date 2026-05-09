package io.k2dv.garden.audit.repository;

import io.k2dv.garden.audit.model.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.UUID;

public interface AuditLogRepository extends JpaRepository<AuditLog, UUID>,
        JpaSpecificationExecutor<AuditLog> {

    Page<AuditLog> findByEntityTypeAndEntityId(String entityType, String entityId, Pageable pageable);
}
