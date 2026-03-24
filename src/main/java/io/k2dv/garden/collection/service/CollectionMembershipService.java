package io.k2dv.garden.collection.service;

import io.k2dv.garden.collection.model.Collection;
import io.k2dv.garden.collection.model.CollectionProduct;
import io.k2dv.garden.collection.model.CollectionRule;
import io.k2dv.garden.collection.model.CollectionType;
import io.k2dv.garden.collection.repository.CollectionProductRepository;
import io.k2dv.garden.collection.repository.CollectionRepository;
import io.k2dv.garden.collection.repository.CollectionRuleRepository;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.model.ProductTag;
import io.k2dv.garden.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CollectionMembershipService {

    private final CollectionRepository collectionRepo;
    private final CollectionRuleRepository ruleRepo;
    private final CollectionProductRepository cpRepo;
    private final ProductRepository productRepo;

    /**
     * Pure evaluation — package-private for unit tests.
     */
    static boolean evaluate(Set<String> tagNames, List<CollectionRule> rules, boolean disjunctive) {
        if (rules.isEmpty()) return false;
        Set<String> lowerTags = tagNames.stream().map(String::toLowerCase).collect(Collectors.toSet());
        if (disjunctive) {
            return rules.stream().anyMatch(r -> matchesRule(lowerTags, r));
        } else {
            return rules.stream().allMatch(r -> matchesRule(lowerTags, r));
        }
    }

    private static boolean matchesRule(Set<String> lowerTags, CollectionRule rule) {
        String lowerValue = rule.getValue().toLowerCase();
        return switch (rule.getOperator()) {
            case EQUALS     -> lowerTags.contains(lowerValue);
            case NOT_EQUALS -> !lowerTags.contains(lowerValue);
            case CONTAINS   -> lowerTags.stream().anyMatch(t -> t.contains(lowerValue));
        };
    }

    /**
     * Full sync for one automated collection: recompute all qualifying products.
     * Called when a collection's rules change.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void syncCollectionMembership(UUID collectionId) {
        Collection collection = collectionRepo.findById(collectionId)
            .orElseThrow(() -> new java.util.NoSuchElementException("Collection not found: " + collectionId));
        if (collection.getCollectionType() != CollectionType.AUTOMATED) {
            return;
        }
        List<CollectionRule> rules = ruleRepo.findByCollectionIdOrderByCreatedAtAsc(collectionId);

        if (rules.isEmpty()) {
            cpRepo.deleteByCollectionId(collectionId);
            return;
        }

        List<Product> allProducts = productRepo.findAllByStatusAndDeletedAtIsNull(ProductStatus.ACTIVE);

        Set<UUID> qualifyingIds = allProducts.stream()
            .filter(p -> {
                Set<String> tagNames = p.getTags().stream()
                    .map(ProductTag::getName).collect(Collectors.toSet());
                return evaluate(tagNames, rules, collection.isDisjunctive());
            })
            .map(Product::getId)
            .collect(Collectors.toSet());

        List<CollectionProduct> existing = cpRepo.findByCollectionId(collectionId);
        Set<UUID> existingIds = existing.stream()
            .map(CollectionProduct::getProductId).collect(Collectors.toSet());

        Set<UUID> toRemove = new HashSet<>(existingIds);
        toRemove.removeAll(qualifyingIds);
        Set<UUID> toAdd = new HashSet<>(qualifyingIds);
        toAdd.removeAll(existingIds);

        if (!toRemove.isEmpty()) {
            cpRepo.deleteByCollectionIdAndProductIdIn(collectionId, toRemove);
        }

        if (!toAdd.isEmpty()) {
            Integer maxPos = cpRepo.findMaxPositionByCollectionId(collectionId);
            int nextPos = (maxPos != null ? maxPos : 0) + 1;

            List<Product> toAddSorted = productRepo.findAllById(toAdd).stream()
                .sorted(Comparator.comparing(Product::getCreatedAt))
                .toList();

            for (Product p : toAddSorted) {
                CollectionProduct cp = new CollectionProduct();
                cp.setCollectionId(collectionId);
                cp.setProductId(p.getId());
                cp.setPosition(nextPos++);
                cpRepo.save(cp);
            }
        }
    }

    /**
     * Re-evaluate one product across all AUTOMATED collections.
     * Called by ProductService when a product's tags change.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void syncCollectionsForProduct(UUID productId, Set<String> newTagNames) {
        List<Collection> automated = collectionRepo.findAllByCollectionTypeAndDeletedAtIsNull(CollectionType.AUTOMATED);
        for (Collection collection : automated) {
            List<CollectionRule> rules = ruleRepo.findByCollectionIdOrderByCreatedAtAsc(collection.getId());
            boolean qualifies = evaluate(newTagNames, rules, collection.isDisjunctive());
            boolean isMember  = cpRepo.existsByCollectionIdAndProductId(collection.getId(), productId);

            if (qualifies && !isMember) {
                Integer maxPos = cpRepo.findMaxPositionByCollectionId(collection.getId());
                CollectionProduct cp = new CollectionProduct();
                cp.setCollectionId(collection.getId());
                cp.setProductId(productId);
                cp.setPosition((maxPos != null ? maxPos : 0) + 1);
                cpRepo.save(cp);
            } else if (!qualifies && isMember) {
                cpRepo.deleteByCollectionIdAndProductId(collection.getId(), productId);
            }
        }
    }

    /**
     * Remove a product from ALL collections (manual + automated).
     * Called when a product is soft-deleted or set to ARCHIVED.
     */
    @Transactional(propagation = Propagation.REQUIRED)
    public void removeProductFromAllCollections(UUID productId) {
        cpRepo.deleteByProductId(productId);
    }
}
