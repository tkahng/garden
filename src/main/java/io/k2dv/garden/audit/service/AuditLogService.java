package io.k2dv.garden.audit.service;

import io.k2dv.garden.audit.model.AuditLog;
import io.k2dv.garden.audit.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
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
}
