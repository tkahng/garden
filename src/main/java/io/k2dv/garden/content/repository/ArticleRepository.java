package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.Article;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<Article, UUID>,
        JpaSpecificationExecutor<Article> {
    Optional<Article> findByIdAndBlogIdAndDeletedAtIsNull(UUID id, UUID blogId);
    boolean existsByHandleAndBlogIdAndDeletedAtIsNull(String handle, UUID blogId);
    boolean existsByHandleAndBlogIdAndDeletedAtIsNullAndIdNot(String handle, UUID blogId, UUID id);

    @Query(value = """
        SELECT * FROM content.articles
        WHERE deleted_at IS NULL AND status = 'PUBLISHED'
          AND search_vector @@ plainto_tsquery('english', :query)
        ORDER BY ts_rank(search_vector, plainto_tsquery('english', :query)) DESC
        """,
        countQuery = """
        SELECT COUNT(*) FROM content.articles
        WHERE deleted_at IS NULL AND status = 'PUBLISHED'
          AND search_vector @@ plainto_tsquery('english', :query)
        """,
        nativeQuery = true)
    Page<Article> fullTextSearch(@Param("query") String query, Pageable pageable);
}
