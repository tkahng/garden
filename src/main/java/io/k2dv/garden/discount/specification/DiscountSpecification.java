package io.k2dv.garden.discount.specification;

import io.k2dv.garden.discount.dto.DiscountFilter;
import io.k2dv.garden.discount.model.Discount;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class DiscountSpecification {

    private DiscountSpecification() {}

    public static Specification<Discount> toSpec(DiscountFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f != null) {
                if (f.type() != null) {
                    predicates.add(cb.equal(root.get("type"), f.type()));
                }
                if (f.isActive() != null) {
                    predicates.add(cb.equal(root.get("isActive"), f.isActive()));
                }
                if (f.codeContains() != null && !f.codeContains().isBlank()) {
                    predicates.add(cb.like(cb.upper(root.get("code")),
                        "%" + f.codeContains().toUpperCase() + "%"));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
