package io.k2dv.garden.collection.specification;

import io.k2dv.garden.collection.dto.request.CollectionFilterRequest;
import io.k2dv.garden.collection.model.Collection;
import io.k2dv.garden.collection.model.CollectionStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class CollectionSpecification {

    private CollectionSpecification() {}

    public static Specification<Collection> toSpec(CollectionFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (f != null) {
                if (f.collectionType() != null)
                    predicates.add(cb.equal(root.get("collectionType"), f.collectionType()));
                if (f.status() != null)
                    predicates.add(cb.equal(root.get("status"), f.status()));
                if (f.titleContains() != null && !f.titleContains().isBlank())
                    predicates.add(cb.like(cb.lower(root.get("title")),
                        "%" + f.titleContains().toLowerCase() + "%"));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Collection> storefrontSpec() {
        return (root, query, cb) -> cb.and(
            cb.isNull(root.get("deletedAt")),
            cb.equal(root.get("status"), CollectionStatus.ACTIVE)
        );
    }
}
