package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.SitePage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface PageRepository extends JpaRepository<SitePage, UUID>,
        JpaSpecificationExecutor<SitePage> {
    Optional<SitePage> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByHandleAndDeletedAtIsNull(String handle);
    boolean existsByHandleAndDeletedAtIsNullAndIdNot(String handle, UUID id);
}
