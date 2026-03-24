package io.k2dv.garden.content.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Table(name = "pages")
@Getter
@Setter
public class SitePage extends BaseEntity {
    @Column(nullable = false)
    private String title;
    @Column(nullable = false)
    private String handle;
    @Column(columnDefinition = "TEXT")
    private String body;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PageStatus status = PageStatus.DRAFT;
    @Column(name = "meta_title")
    private String metaTitle;
    @Column(name = "meta_description")
    private String metaDescription;
    @Column(name = "published_at")
    private Instant publishedAt;
    @Column(name = "deleted_at")
    private Instant deletedAt;
}
