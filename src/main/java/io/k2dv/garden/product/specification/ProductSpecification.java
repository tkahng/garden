package io.k2dv.garden.product.specification;

import io.k2dv.garden.b2b.model.CompanyProductCatalog;
import io.k2dv.garden.product.dto.ProductFilterRequest;
import io.k2dv.garden.product.dto.StorefrontProductFilterRequest;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductStatus;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ProductSpecification {

    private ProductSpecification() {}

    public static Specification<Product> toSpec(ProductFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (f != null) {
                if (f.status() != null) {
                    predicates.add(cb.equal(root.get("status"), f.status()));
                }
                if (f.titleContains() != null && !f.titleContains().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("title")),
                            "%" + f.titleContains().toLowerCase() + "%"));
                }
                if (f.vendor() != null && !f.vendor().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("vendor")),
                            "%" + f.vendor().toLowerCase() + "%"));
                }
                if (f.productType() != null && !f.productType().isBlank()) {
                    predicates.add(cb.equal(root.get("productType"), f.productType()));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Product> storefrontSpec(StorefrontProductFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            predicates.add(cb.equal(root.get("status"), ProductStatus.ACTIVE));
            if (f != null) {
                if (f.titleContains() != null && !f.titleContains().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("title")),
                            "%" + f.titleContains().toLowerCase() + "%"));
                }
                if (f.vendor() != null && !f.vendor().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("vendor")),
                            "%" + f.vendor().toLowerCase() + "%"));
                }
                if (f.productType() != null && !f.productType().isBlank()) {
                    predicates.add(cb.equal(root.get("productType"), f.productType()));
                }

                // Catalog visibility: products in any catalog are only visible to their company
                Subquery<UUID> anyCompanySq = query.subquery(UUID.class);
                var anyRoot = anyCompanySq.from(CompanyProductCatalog.class);
                anyCompanySq.select(anyRoot.get("productId")).distinct(true);

                UUID companyId = f.companyId();
                if (companyId != null) {
                    Subquery<UUID> thisCompanySq = query.subquery(UUID.class);
                    var thisRoot = thisCompanySq.from(CompanyProductCatalog.class);
                    thisCompanySq.select(thisRoot.get("productId"))
                        .where(cb.equal(thisRoot.get("companyId"), companyId));

                    predicates.add(cb.or(
                        cb.not(root.get("id").in(anyCompanySq)),
                        root.get("id").in(thisCompanySq)
                    ));
                } else {
                    predicates.add(cb.not(root.get("id").in(anyCompanySq)));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
