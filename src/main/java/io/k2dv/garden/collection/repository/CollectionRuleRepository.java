package io.k2dv.garden.collection.repository;

import io.k2dv.garden.collection.model.CollectionRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CollectionRuleRepository extends JpaRepository<CollectionRule, UUID> {
    List<CollectionRule> findByCollectionIdOrderByCreatedAtAsc(UUID collectionId);
    void deleteByCollectionId(UUID collectionId);
}
