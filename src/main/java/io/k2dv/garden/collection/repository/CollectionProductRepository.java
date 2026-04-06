package io.k2dv.garden.collection.repository;

import io.k2dv.garden.collection.model.CollectionProduct;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @Query("SELECT cp.collectionId AS collectionId, COUNT(cp) AS productCount FROM CollectionProduct cp WHERE cp.collectionId IN :collectionIds GROUP BY cp.collectionId")
    List<CollectionCount> countByCollectionIdIn(@Param("collectionIds") java.util.Collection<UUID> collectionIds);

    interface CollectionCount {
        UUID getCollectionId();
        long getProductCount();
    }

    @Query("SELECT cp FROM CollectionProduct cp " +
           "WHERE cp.collectionId = :collectionId " +
           "AND EXISTS (" +
           "  SELECT p FROM io.k2dv.garden.product.model.Product p " +
           "  WHERE p.id = cp.productId AND p.status = io.k2dv.garden.product.model.ProductStatus.ACTIVE AND p.deletedAt IS NULL" +
           ") " +
           "ORDER BY cp.position ASC, cp.createdAt ASC")
    Page<CollectionProduct> findActiveProductsByCollectionId(@Param("collectionId") UUID collectionId, Pageable pageable);
}
