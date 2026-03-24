package io.k2dv.garden.content.specification;

import io.k2dv.garden.content.dto.BlogFilterRequest;
import io.k2dv.garden.content.model.Blog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class BlogSpecification {

    private BlogSpecification() {}

    public static Specification<Blog> toSpec(BlogFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (f != null) {
                if (f.titleContains() != null && !f.titleContains().isBlank()) {
                    String pattern = "%" + f.titleContains().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("title")), pattern));
                }
                if (f.handleContains() != null && !f.handleContains().isBlank()) {
                    String pattern = "%" + f.handleContains().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("handle")), pattern));
                }
            }

            return predicates.isEmpty()
                ? cb.conjunction()
                : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
