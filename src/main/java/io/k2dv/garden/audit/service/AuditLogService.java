package io.k2dv.garden.audit.service;

import io.k2dv.garden.audit.dto.AuditLogResponse;
import io.k2dv.garden.audit.model.AuditLog;
import io.k2dv.garden.audit.repository.AuditLogRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Persists and queries the append-only audit log that records every admin mutation
 * captured by the {@link io.k2dv.garden.audit.aspect.AuditAspect}.
 * Writes always run in a new, independent transaction so that an audit record is
 * committed even if the calling transaction rolls back.
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repo;

    /**
     * Writes a single immutable audit entry in its own transaction ({@code REQUIRES_NEW})
     * so the log survives even when the outer business transaction is rolled back.
     * Typically called by {@link io.k2dv.garden.audit.aspect.AuditAspect} rather than directly.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(UUID actorId, String actorEmail, String action,
                       String entityType, String entityId,
                       String beforeJson, String afterJson) {
        AuditLog entry = new AuditLog();
        entry.setActorId(actorId);
        entry.setActorEmail(actorEmail);
        entry.setAction(action);
        entry.setEntityType(entityType);
        entry.setEntityId(entityId);
        entry.setBeforeJson(beforeJson);
        entry.setAfterJson(afterJson);
        repo.save(entry);
    }

    /**
     * Queries the audit log with optional filters for entity type, entity ID, and actor email,
     * returning results in reverse-chronological order for the admin audit trail UI.
     */
    @Transactional(readOnly = true)
    public PagedResult<AuditLogResponse> list(String entityType, String entityId,
                                              String actorEmail, Pageable pageable) {
        Specification<AuditLog> spec = (root, q, cb) -> cb.conjunction();
        if (entityType != null && !entityType.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityType"), entityType.trim()));
        }
        if (entityId != null && !entityId.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("entityId"), entityId.trim()));
        }
        if (actorEmail != null && !actorEmail.isBlank()) {
            String pattern = "%" + actorEmail.trim().toLowerCase() + "%";
            spec = spec.and((root, q, cb) -> cb.like(cb.lower(root.get("actorEmail")), pattern));
        }
        return PagedResult.of(repo.findAll(spec, pageable), this::toResponse);
    }

    private AuditLogResponse toResponse(AuditLog e) {
        return new AuditLogResponse(
            e.getId(), e.getActorId(), e.getActorEmail(),
            e.getAction(), e.getEntityType(), e.getEntityId(),
            e.getBeforeJson(), e.getAfterJson(), e.getCreatedAt());
    }
}
