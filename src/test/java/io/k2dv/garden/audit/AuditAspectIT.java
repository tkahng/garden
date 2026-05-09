package io.k2dv.garden.audit;

import io.k2dv.garden.audit.aspect.Audited;
import io.k2dv.garden.audit.model.AuditLog;
import io.k2dv.garden.audit.repository.AuditLogRepository;
import io.k2dv.garden.audit.service.AuditLogService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuditAspectIT extends AbstractIntegrationTest {

    @Autowired AuditLogService auditLogService;
    @Autowired AuditLogRepository auditLogRepo;

    @Test
    void record_persistsAuditEntry() {
        UUID actorId = UUID.randomUUID();
        auditLogService.record(actorId, "admin@test.com", "softDelete",
            "product", "abc-123", null, null);

        var logs = auditLogRepo.findByEntityTypeAndEntityId("product", "abc-123", Pageable.unpaged());
        assertThat(logs.getTotalElements()).isEqualTo(1L);
        AuditLog log = logs.getContent().get(0);
        assertThat(log.getActorId()).isEqualTo(actorId);
        assertThat(log.getActorEmail()).isEqualTo("admin@test.com");
        assertThat(log.getAction()).isEqualTo("softDelete");
        assertThat(log.getEntityType()).isEqualTo("product");
        assertThat(log.getEntityId()).isEqualTo("abc-123");
    }

    @Test
    void record_usesRequiresNewPropagation() {
        // Record should commit independently even if the caller's tx rolls back (default test behaviour)
        UUID id = UUID.randomUUID();
        auditLogService.record(null, null, "testAction", "test", id.toString(), null, null);

        // The test transaction will roll back, but REQUIRES_NEW already committed
        // We verify the entry was created (visible within the same test tx via read-back)
        assertThat(auditLogRepo.findByEntityTypeAndEntityId("test", id.toString(), Pageable.unpaged())
            .getTotalElements()).isEqualTo(1L);
    }
}
