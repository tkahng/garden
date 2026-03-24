package io.k2dv.garden.content.specification;

import io.k2dv.garden.content.dto.ArticleFilterRequest;
import io.k2dv.garden.content.model.Article;
import io.k2dv.garden.content.model.ArticleStatus;
import io.k2dv.garden.content.model.ContentTag;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ArticleSpecification {

    private ArticleSpecification() {}

    public static Specification<Article> toSpec(UUID blogId, ArticleFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));
            if (blogId != null) {
                predicates.add(cb.equal(root.get("blogId"), blogId));
            }

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
                if (f.authorId() != null) {
                    predicates.add(cb.equal(root.get("authorId"), f.authorId()));
                }
                if (f.tag() != null && !f.tag().isBlank()) {
                    // JOIN through article_content_tags → content_tags using the @ManyToMany path
                    Join<Article, ContentTag> tagJoin = root.join("tags", JoinType.INNER);
                    predicates.add(cb.equal(tagJoin.get("name"), f.tag()));
                    // Ensure count query uses DISTINCT to avoid inflated pagination totals
                    if (query != null) query.distinct(true);
                }
                if (f.q() != null && !f.q().isBlank()) {
                    String pattern = "%" + f.q().toLowerCase() + "%";
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")),   pattern),
                        cb.like(cb.lower(root.get("excerpt")), pattern),
                        cb.like(cb.lower(root.get("body")),    pattern)
                    ));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<Article> publishedSpec(UUID blogId) {
        return (root, query, cb) -> cb.and(
            cb.isNull(root.get("deletedAt")),
            cb.equal(root.get("blogId"), blogId),
            cb.equal(root.get("status"), ArticleStatus.PUBLISHED)
        );
    }
}
