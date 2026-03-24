package io.k2dv.garden.admin.user.specification;

import io.k2dv.garden.admin.user.dto.UserFilter;
import io.k2dv.garden.user.model.User;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class UserSpecification {

    private UserSpecification() {}

    public static Specification<User> toSpec(UserFilter f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (f != null) {
                if (f.status() != null) {
                    predicates.add(cb.equal(root.get("status"), f.status()));
                }
                if (f.email() != null && !f.email().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("email")),
                            "%" + f.email().toLowerCase() + "%"));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
