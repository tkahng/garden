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

@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository repo;

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
