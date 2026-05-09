package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.SitePage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface PageRepository extends JpaRepository<SitePage, UUID>,
        JpaSpecificationExecutor<SitePage> {
    Optional<SitePage> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByHandleAndDeletedAtIsNull(String handle);
    boolean existsByHandleAndDeletedAtIsNullAndIdNot(String handle, UUID id);

    @Query(value = """
        SELECT * FROM content.pages
        WHERE deleted_at IS NULL AND status = 'PUBLISHED'
          AND search_vector @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM content.pages
        WHERE deleted_at IS NULL AND status = 'PUBLISHED'
          AND search_vector @@ plainto_tsquery('english', :query)
        """,
        nativeQuery = true)
    Page<SitePage> fullTextSearch(@Param("query") String query, Pageable pageable);
}
