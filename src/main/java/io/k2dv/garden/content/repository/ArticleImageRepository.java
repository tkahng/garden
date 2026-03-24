package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.ArticleImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ArticleImageRepository extends JpaRepository<ArticleImage, UUID> {
    int countByArticleId(UUID articleId);
    List<ArticleImage> findByArticleIdOrderByPositionAsc(UUID articleId);
}
