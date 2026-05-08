package io.k2dv.garden.collection.repository;

import io.k2dv.garden.collection.model.Collection;
import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<Collection, UUID>, JpaSpecificationExecutor<Collection> {
    Optional<Collection> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Collection> findByHandleAndDeletedAtIsNullAndStatus(String handle, CollectionStatus status);
    boolean existsByHandleAndDeletedAtIsNull(String handle);
    boolean existsByHandleAndDeletedAtIsNullAndIdNot(String handle, UUID id);
    List<Collection> findAllByCollectionTypeAndDeletedAtIsNull(CollectionType type);

    @Query(value = """
        SELECT * FROM catalog.collections
        WHERE deleted_at IS NULL AND status = 'ACTIVE'
          AND search_vector @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM catalog.collections
        WHERE deleted_at IS NULL AND status = 'ACTIVE'
          AND search_vector @@ plainto_tsquery('english', :query)
        """,
        nativeQuery = true)
    Page<Collection> fullTextSearch(@Param("query") String query, Pageable pageable);
}
