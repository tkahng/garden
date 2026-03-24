package io.k2dv.garden.collection.repository;

import io.k2dv.garden.collection.model.Collection;
import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionRepository extends JpaRepository<Collection, UUID>, JpaSpecificationExecutor<Collection> {
    Optional<Collection> findByIdAndDeletedAtIsNull(UUID id);
    Optional<Collection> findByHandleAndDeletedAtIsNullAndStatus(String handle, CollectionStatus status);
    boolean existsByHandleAndDeletedAtIsNull(String handle);
    boolean existsByHandleAndDeletedAtIsNullAndIdNot(String handle, UUID id);
    List<Collection> findAllByCollectionTypeAndDeletedAtIsNull(CollectionType type);
}
