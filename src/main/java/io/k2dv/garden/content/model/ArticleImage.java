package io.k2dv.garden.content.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(schema = "content", name = "article_images")
@Getter
@Setter
public class ArticleImage extends BaseEntity {
    @Column(name = "article_id", nullable = false)
    private UUID articleId;
    @Column(name = "blob_id", nullable = false)
    private UUID blobId;
    @Column(name = "alt_text")
    private String altText;
    @Column(nullable = false)
    private int position;
}
