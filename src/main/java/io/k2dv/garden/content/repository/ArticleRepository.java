package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.Article;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface ArticleRepository extends JpaRepository<Article, UUID>,
        JpaSpecificationExecutor<Article> {
    Optional<Article> findByIdAndBlogIdAndDeletedAtIsNull(UUID id, UUID blogId);
    boolean existsByHandleAndBlogIdAndDeletedAtIsNull(String handle, UUID blogId);
    boolean existsByHandleAndBlogIdAndDeletedAtIsNullAndIdNot(String handle, UUID blogId, UUID id);
}
