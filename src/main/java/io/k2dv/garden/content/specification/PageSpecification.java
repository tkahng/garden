package io.k2dv.garden.content.specification;

import io.k2dv.garden.content.dto.PageFilterRequest;
import io.k2dv.garden.content.model.SitePage;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class PageSpecification {

    private PageSpecification() {}

    public static Specification<SitePage> toSpec(PageFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

            if (f != null) {
                if (f.status() != null) {
                    predicates.add(cb.equal(root.get("status"), f.status()));
                }
                if (f.titleContains() != null && !f.titleContains().isBlank()) {
                    String pattern = "%" + f.titleContains().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("title")), pattern));
                }
                if (f.handleContains() != null && !f.handleContains().isBlank()) {
                    String pattern = "%" + f.handleContains().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("handle")), pattern));
                }
                if (f.q() != null && !f.q().isBlank()) {
                    String pattern = "%" + f.q().toLowerCase() + "%";
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("body")),  pattern)
                    ));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<SitePage> publishedSpec() {
        return (root, query, cb) -> cb.and(
            cb.isNull(root.get("deletedAt")),
            cb.equal(root.get("status"), io.k2dv.garden.content.model.PageStatus.PUBLISHED)
        );
    }
}
