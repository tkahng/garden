package io.k2dv.garden.content.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(schema = "content", name = "articles")
@Getter
@Setter
public class Article extends BaseEntity {
    @Column(name = "blog_id", nullable = false)
    private UUID blogId;
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String handle;
    @Column(columnDefinition = "TEXT")
    private String body;
    @Column(columnDefinition = "TEXT")
    private String excerpt;
    @Column(name = "author_id")
    private UUID authorId;
    @Column(name = "author_name")
    private String authorName;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ArticleStatus status = ArticleStatus.DRAFT;
    @Column(name = "featured_image_id")
    private UUID featuredImageId;
    @Column(name = "meta_title")
    private String metaTitle;
    @Column(name = "meta_description")
    private String metaDescription;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        schema = "content",
        name = "article_content_tags",
        joinColumns = @JoinColumn(name = "article_id"),
        inverseJoinColumns = @JoinColumn(name = "content_tag_id")
    )
    private Set<ContentTag> tags = new LinkedHashSet<>();
}
