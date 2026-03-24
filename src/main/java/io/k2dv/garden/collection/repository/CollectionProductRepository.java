package io.k2dv.garden.collection.repository;

import io.k2dv.garden.collection.model.CollectionProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CollectionProductRepository extends JpaRepository<CollectionProduct, UUID> {
    Page<CollectionProduct> findByCollectionIdOrderByPositionAscCreatedAtAsc(UUID collectionId, Pageable pageable);
    List<CollectionProduct> findByCollectionId(UUID collectionId);
    Optional<CollectionProduct> findByCollectionIdAndProductId(UUID collectionId, UUID productId);
    boolean existsByCollectionIdAndProductId(UUID collectionId, UUID productId);
    long countByCollectionId(UUID collectionId);
    void deleteByCollectionId(UUID collectionId);
    void deleteByCollectionIdAndProductId(UUID collectionId, UUID productId);
    void deleteByCollectionIdAndProductIdIn(UUID collectionId, Collection<UUID> productIds);
    void deleteByProductId(UUID productId);

    @Query("SELECT MAX(cp.position) FROM CollectionProduct cp WHERE cp.collectionId = :collectionId")
    Integer findMaxPositionByCollectionId(UUID collectionId);
}
