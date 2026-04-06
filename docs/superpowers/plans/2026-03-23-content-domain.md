# Content Domain Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement a CMS content domain covering static pages, blogs, articles, article images, and content tags with full CRUD, offset pagination, Specification-based filtering, and both admin and storefront APIs.

**Architecture:** Package-by-feature under `io.k2dv.garden.content`. `PageService` owns pages; `ArticleService` owns blogs + articles; `ArticleImageService` owns article images. All repositories extend `JpaSpecificationExecutor<T>`. Controllers separate admin (`content:*` permissions) from public storefront endpoints. The entity class for pages is named `SitePage` to avoid shadowing Spring Data's `org.springframework.data.domain.Page<T>`.

**Tech Stack:** Spring Boot 4.x, Java 25, Spring Data JPA (`JpaSpecificationExecutor`), Hibernate 6, Flyway, Lombok, PostgreSQL 17 (Testcontainers), MockMvc slice tests.

---

## File Structure

```
src/main/resources/db/migration/
  V10__create_content.sql                       — DDL for all 6 content tables

src/main/java/io/k2dv/garden/content/
  model/
    SitePage.java                               — pages entity (SitePage avoids clash with Spring's Page<T>)
    PageStatus.java                             — DRAFT | PUBLISHED enum
    Blog.java                                   — blogs entity
    Article.java                                — articles entity (ManyToMany → ContentTag)
    ArticleStatus.java                          — DRAFT | PUBLISHED enum
    ArticleImage.java                           — article_images entity
    ContentTag.java                             — content_tags entity
  repository/
    PageRepository.java                         — extends JpaRepository + JpaSpecificationExecutor
    BlogRepository.java                         — same
    ArticleRepository.java                      — same; has existsByHandleAndBlogIdAndDeletedAtIsNull
    ArticleImageRepository.java                 — countByArticleId, findByArticleIdOrderByPositionAsc
    ContentTagRepository.java                   — findByName
  dto/
    CreatePageRequest.java
    UpdatePageRequest.java
    PageStatusRequest.java
    AdminPageResponse.java
    PageResponse.java
    PageFilterRequest.java
    CreateBlogRequest.java
    UpdateBlogRequest.java
    AdminBlogResponse.java
    BlogResponse.java
    BlogFilterRequest.java
    CreateArticleRequest.java
    UpdateArticleRequest.java
    ArticleStatusRequest.java
    AdminArticleResponse.java
    ArticleResponse.java
    ArticleFilterRequest.java
    CreateArticleImageRequest.java
    ArticleImagePositionItem.java
    ArticleImageResponse.java
  specification/
    PageSpecification.java                      — toSpec(PageFilterRequest) → Specification<SitePage>
    BlogSpecification.java                      — toSpec(BlogFilterRequest) → Specification<Blog>
    ArticleSpecification.java                   — toSpec(UUID blogId, ArticleFilterRequest) → Specification<Article>
  service/
    PageService.java
    ArticleService.java
    ArticleImageService.java
  controller/
    AdminPageController.java                    — /api/v1/admin/pages
    AdminBlogController.java                    — /api/v1/admin/blogs (blogs + nested articles + images)
    StorefrontPageController.java               — /api/v1/pages
    StorefrontBlogController.java               — /api/v1/blogs

src/test/java/io/k2dv/garden/content/
  service/
    PageServiceIT.java
    ArticleServiceIT.java                       — covers blogs, articles, images, tags
  controller/
    AdminPageControllerTest.java
    AdminBlogControllerTest.java
    StorefrontContentControllerTest.java
```

---

## Task 1: Flyway Migration V10

**Files:**
- Create: `src/main/resources/db/migration/V10__create_content.sql`

- [ ] **Step 1: Write the migration**

```sql
-- src/main/resources/db/migration/V10__create_content.sql

CREATE TABLE pages (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title            TEXT        NOT NULL,
    handle           TEXT        NOT NULL,
    body             TEXT,
    status           TEXT        NOT NULL DEFAULT 'DRAFT',
    meta_title       TEXT,
    meta_description TEXT,
    published_at     TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON pages
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE blogs (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    title      TEXT        NOT NULL,
    handle     TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON blogs
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE articles (
    id               UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    blog_id          UUID        NOT NULL REFERENCES blogs(id) ON DELETE CASCADE,
    title            TEXT        NOT NULL,
    handle           TEXT        NOT NULL,
    body             TEXT,
    excerpt          TEXT,
    author_id        UUID        REFERENCES users(id) ON DELETE SET NULL,
    author_name      TEXT,
    status           TEXT        NOT NULL DEFAULT 'DRAFT',
    featured_image_id UUID,
    meta_title       TEXT,
    meta_description TEXT,
    published_at     TIMESTAMPTZ,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON articles
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE article_images (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    article_id UUID        NOT NULL REFERENCES articles(id) ON DELETE CASCADE,
    blob_id    UUID        NOT NULL REFERENCES blob_objects(id),
    alt_text   TEXT,
    position   INT         NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON article_images
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE content_tags (
    id         UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    name       TEXT        NOT NULL UNIQUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON content_tags
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE article_content_tags (
    article_id     UUID NOT NULL REFERENCES articles(id),
    content_tag_id UUID NOT NULL REFERENCES content_tags(id),
    PRIMARY KEY (article_id, content_tag_id)
);
```

- [ ] **Step 2: Verify migration runs**

```bash
./mvnw test -pl . -Dtest=GardenApplicationTests -q
```

Expected: BUILD SUCCESS (Flyway applies V10 against Testcontainers PG).

- [ ] **Step 3: Commit**

```bash
git add src/main/resources/db/migration/V10__create_content.sql
git commit -m "feat(content): add V10 migration for content domain tables"
```

---

## Task 2: JPA Entities and Repositories

**Files:**
- Create: `src/main/java/io/k2dv/garden/content/model/PageStatus.java`
- Create: `src/main/java/io/k2dv/garden/content/model/SitePage.java`
- Create: `src/main/java/io/k2dv/garden/content/model/Blog.java`
- Create: `src/main/java/io/k2dv/garden/content/model/ArticleStatus.java`
- Create: `src/main/java/io/k2dv/garden/content/model/ContentTag.java`
- Create: `src/main/java/io/k2dv/garden/content/model/Article.java`
- Create: `src/main/java/io/k2dv/garden/content/model/ArticleImage.java`
- Create: `src/main/java/io/k2dv/garden/content/repository/PageRepository.java`
- Create: `src/main/java/io/k2dv/garden/content/repository/BlogRepository.java`
- Create: `src/main/java/io/k2dv/garden/content/repository/ArticleRepository.java`
- Create: `src/main/java/io/k2dv/garden/content/repository/ArticleImageRepository.java`
- Create: `src/main/java/io/k2dv/garden/content/repository/ContentTagRepository.java`

- [ ] **Step 1: Write the enums**

```java
// PageStatus.java
package io.k2dv.garden.content.model;
public enum PageStatus { DRAFT, PUBLISHED }

// ArticleStatus.java
package io.k2dv.garden.content.model;
public enum ArticleStatus { DRAFT, PUBLISHED }
```

- [ ] **Step 2: Write the SitePage entity**

Note: named `SitePage` (not `Page`) to avoid shadowing `org.springframework.data.domain.Page<T>` in service/repo imports.

```java
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
```

- [ ] **Step 3: Write Blog entity**

```java
package io.k2dv.garden.content.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "blogs")
@Getter
@Setter
public class Blog extends BaseEntity {
    @Column(nullable = false)
    private String title;
    @Column(nullable = false, unique = true)
    private String handle;
}
```

- [ ] **Step 4: Write ContentTag entity**

```java
package io.k2dv.garden.content.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "content_tags")
@Getter
@Setter
public class ContentTag extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String name;
}
```

- [ ] **Step 5: Write Article entity**

```java
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
@Table(name = "articles")
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
        name = "article_content_tags",
        joinColumns = @JoinColumn(name = "article_id"),
        inverseJoinColumns = @JoinColumn(name = "content_tag_id")
    )
    private Set<ContentTag> tags = new LinkedHashSet<>();
}
```

- [ ] **Step 6: Write ArticleImage entity**

```java
package io.k2dv.garden.content.model;

import io.k2dv.garden.shared.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "article_images")
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
```

- [ ] **Step 7: Write repositories**

```java
// PageRepository.java
package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.SitePage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface PageRepository extends JpaRepository<SitePage, UUID>,
        JpaSpecificationExecutor<SitePage> {
    Optional<SitePage> findByIdAndDeletedAtIsNull(UUID id);
    boolean existsByHandleAndDeletedAtIsNull(String handle);
    boolean existsByHandleAndDeletedAtIsNullAndIdNot(String handle, UUID id);
}
```

```java
// BlogRepository.java
package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.Blog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;
import java.util.UUID;

public interface BlogRepository extends JpaRepository<Blog, UUID>,
        JpaSpecificationExecutor<Blog> {
    Optional<Blog> findByHandle(String handle);
    boolean existsByHandle(String handle);
    boolean existsByHandleAndIdNot(String handle, UUID id);
}
```

```java
// ArticleRepository.java
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
```

```java
// ArticleImageRepository.java
package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.ArticleImage;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ArticleImageRepository extends JpaRepository<ArticleImage, UUID> {
    int countByArticleId(UUID articleId);
    List<ArticleImage> findByArticleIdOrderByPositionAsc(UUID articleId);
}
```

```java
// ContentTagRepository.java
package io.k2dv.garden.content.repository;

import io.k2dv.garden.content.model.ContentTag;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.UUID;

public interface ContentTagRepository extends JpaRepository<ContentTag, UUID> {
    Optional<ContentTag> findByName(String name);
}
```

- [ ] **Step 8: Verify compilation**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 9: Commit**

```bash
git add src/main/java/io/k2dv/garden/content/model/ \
        src/main/java/io/k2dv/garden/content/repository/
git commit -m "feat(content): add JPA entities and repositories"
```

---

## Task 3: DTOs

**Files:**
- Create all files under `src/main/java/io/k2dv/garden/content/dto/`

- [ ] **Step 1: Write request records**

```java
// CreatePageRequest.java
package io.k2dv.garden.content.dto;
import jakarta.validation.constraints.NotBlank;
public record CreatePageRequest(
    @NotBlank String title,
    String handle,
    String body,
    String metaTitle,
    String metaDescription
) {}

// UpdatePageRequest.java
package io.k2dv.garden.content.dto;
public record UpdatePageRequest(
    String title,
    String handle,
    String body,
    String metaTitle,
    String metaDescription
) {}

// PageStatusRequest.java
package io.k2dv.garden.content.dto;
import io.k2dv.garden.content.model.PageStatus;
import jakarta.validation.constraints.NotNull;
public record PageStatusRequest(@NotNull PageStatus status) {}

// CreateBlogRequest.java
package io.k2dv.garden.content.dto;
import jakarta.validation.constraints.NotBlank;
public record CreateBlogRequest(@NotBlank String title, String handle) {}

// UpdateBlogRequest.java
package io.k2dv.garden.content.dto;
public record UpdateBlogRequest(String title, String handle) {}

// CreateArticleRequest.java
package io.k2dv.garden.content.dto;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.UUID;
public record CreateArticleRequest(
    @NotBlank String title,
    String handle,
    String body,
    String excerpt,
    UUID authorId,
    String metaTitle,
    String metaDescription,
    List<String> tags
) {}

// UpdateArticleRequest.java
package io.k2dv.garden.content.dto;
import java.util.List;
import java.util.UUID;
public record UpdateArticleRequest(
    String title,
    String handle,
    String body,
    String excerpt,
    UUID authorId,
    String metaTitle,
    String metaDescription,
    List<String> tags
) {}

// ArticleStatusRequest.java
package io.k2dv.garden.content.dto;
import io.k2dv.garden.content.model.ArticleStatus;
import jakarta.validation.constraints.NotNull;
public record ArticleStatusRequest(@NotNull ArticleStatus status) {}

// CreateArticleImageRequest.java
package io.k2dv.garden.content.dto;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
public record CreateArticleImageRequest(@NotNull UUID blobId, String altText) {}

// ArticleImagePositionItem.java
package io.k2dv.garden.content.dto;
import java.util.UUID;
public record ArticleImagePositionItem(UUID id, int position) {}
```

- [ ] **Step 2: Write filter request records**

```java
// PageFilterRequest.java
package io.k2dv.garden.content.dto;
import io.k2dv.garden.content.model.PageStatus;
public record PageFilterRequest(PageStatus status, String titleContains, String handleContains, String q) {}

// BlogFilterRequest.java
package io.k2dv.garden.content.dto;
// handleContains is admin-only; storefront controller binds only titleContains
public record BlogFilterRequest(String titleContains, String handleContains) {}

// ArticleFilterRequest.java
package io.k2dv.garden.content.dto;
import io.k2dv.garden.content.model.ArticleStatus;
import java.util.UUID;
public record ArticleFilterRequest(
    ArticleStatus status,
    String titleContains,
    String handleContains,
    UUID authorId,
    String tag,
    String q
) {}
```

- [ ] **Step 3: Write response records**

```java
// AdminPageResponse.java
package io.k2dv.garden.content.dto;
import io.k2dv.garden.content.model.PageStatus;
import java.time.Instant;
import java.util.UUID;
public record AdminPageResponse(
    UUID id, String title, String handle, String body,
    PageStatus status, String metaTitle, String metaDescription,
    Instant publishedAt, Instant createdAt, Instant updatedAt, Instant deletedAt
) {}

// PageResponse.java
package io.k2dv.garden.content.dto;
import java.time.Instant;
import java.util.UUID;
public record PageResponse(
    UUID id, String title, String handle, String body,
    String metaTitle, String metaDescription, Instant publishedAt
) {}

// AdminBlogResponse.java
package io.k2dv.garden.content.dto;
import java.time.Instant;
import java.util.UUID;
public record AdminBlogResponse(UUID id, String title, String handle, Instant createdAt, Instant updatedAt) {}

// BlogResponse.java
package io.k2dv.garden.content.dto;
import java.time.Instant;
import java.util.UUID;
public record BlogResponse(UUID id, String title, String handle, Instant createdAt) {}

// ArticleImageResponse.java
package io.k2dv.garden.content.dto;
import java.util.UUID;
public record ArticleImageResponse(UUID id, String url, String altText, int position) {}

// AdminArticleResponse.java
package io.k2dv.garden.content.dto;
import io.k2dv.garden.content.model.ArticleStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record AdminArticleResponse(
    UUID id, UUID blogId, String title, String handle, String body, String excerpt,
    UUID authorId, String authorName, ArticleStatus status,
    UUID featuredImageId, List<ArticleImageResponse> images, List<String> tags,
    String metaTitle, String metaDescription,
    Instant publishedAt, Instant createdAt, Instant updatedAt, Instant deletedAt
) {}

// ArticleResponse.java
package io.k2dv.garden.content.dto;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
public record ArticleResponse(
    UUID id, UUID blogId, String title, String handle, String body, String excerpt,
    String authorName, UUID featuredImageId, List<ArticleImageResponse> images,
    List<String> tags, String metaTitle, String metaDescription, Instant publishedAt
) {}
```

- [ ] **Step 4: Verify compilation**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/k2dv/garden/content/dto/
git commit -m "feat(content): add content domain DTOs"
```

---

## Task 4: Specification Builders

**Files:**
- Create: `src/main/java/io/k2dv/garden/content/specification/PageSpecification.java`
- Create: `src/main/java/io/k2dv/garden/content/specification/BlogSpecification.java`
- Create: `src/main/java/io/k2dv/garden/content/specification/ArticleSpecification.java`

- [ ] **Step 1: Write PageSpecification**

```java
package io.k2dv.garden.content.specification;

import io.k2dv.garden.content.dto.PageFilterRequest;
import io.k2dv.garden.content.model.SitePage;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class PageSpecification {

    private PageSpecification() {}

    public static Specification<SitePage> toSpec(PageFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isNull(root.get("deletedAt")));

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
                if (f.q() != null && !f.q().isBlank()) {
                    String pattern = "%" + f.q().toLowerCase() + "%";
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("body")),  pattern)
                    ));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public static Specification<SitePage> publishedSpec() {
        return (root, query, cb) -> cb.and(
            cb.isNull(root.get("deletedAt")),
            cb.equal(root.get("status"), io.k2dv.garden.content.model.PageStatus.PUBLISHED)
        );
    }
}
```

- [ ] **Step 2: Write BlogSpecification**

```java
package io.k2dv.garden.content.specification;

import io.k2dv.garden.content.dto.BlogFilterRequest;
import io.k2dv.garden.content.model.Blog;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.util.ArrayList;
import java.util.List;

public class BlogSpecification {

    private BlogSpecification() {}

    public static Specification<Blog> toSpec(BlogFilterRequest f) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (f != null) {
                if (f.titleContains() != null && !f.titleContains().isBlank()) {
                    String pattern = "%" + f.titleContains().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("title")), pattern));
                }
                if (f.handleContains() != null && !f.handleContains().isBlank()) {
                    String pattern = "%" + f.handleContains().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("handle")), pattern));
                }
            }

            return predicates.isEmpty()
                ? cb.conjunction()
                : cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
```

- [ ] **Step 3: Write ArticleSpecification**

```java
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
```

- [ ] **Step 4: Verify compilation**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/k2dv/garden/content/specification/
git commit -m "feat(content): add Specification builders for pages, blogs, articles"
```

---

## Task 5: PageService + PageServiceIT

**Files:**
- Create: `src/main/java/io/k2dv/garden/content/service/PageService.java`
- Create: `src/test/java/io/k2dv/garden/content/service/PageServiceIT.java`

- [ ] **Step 1: Write the failing tests first**

```java
package io.k2dv.garden.content.service;

import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.PageStatus;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageServiceIT extends AbstractIntegrationTest {

    @Autowired PageService pageService;

    @Test
    void createPage_autoHandle_draftStatus() {
        var resp = pageService.create(new CreatePageRequest("Hello World", null, null, null, null));
        assertThat(resp.handle()).isEqualTo("hello-world");
        assertThat(resp.status()).isEqualTo(PageStatus.DRAFT);
    }

    @Test
    void createPage_duplicateHandle_throwsConflict() {
        pageService.create(new CreatePageRequest("About Us", null, null, null, null));
        assertThatThrownBy(() ->
            pageService.create(new CreatePageRequest("About Us", null, null, null, null))
        ).isInstanceOf(ConflictException.class);
    }

    @Test
    void changeStatus_toPublished_setsPublishedAt() {
        var page = pageService.create(new CreatePageRequest("News", null, null, null, null));
        var updated = pageService.changeStatus(page.id(), new PageStatusRequest(PageStatus.PUBLISHED));
        assertThat(updated.status()).isEqualTo(PageStatus.PUBLISHED);
        assertThat(updated.publishedAt()).isNotNull();
    }

    @Test
    void changeStatus_toDraft_clearsPublishedAt() {
        var page = pageService.create(new CreatePageRequest("Events", null, null, null, null));
        pageService.changeStatus(page.id(), new PageStatusRequest(PageStatus.PUBLISHED));
        var reverted = pageService.changeStatus(page.id(), new PageStatusRequest(PageStatus.DRAFT));
        assertThat(reverted.publishedAt()).isNull();
    }

    @Test
    void softDelete_excludedFromStorefrontList() {
        var page = pageService.create(new CreatePageRequest("Gone", null, null, null, null));
        pageService.changeStatus(page.id(), new PageStatusRequest(PageStatus.PUBLISHED));
        pageService.delete(page.id());

        var pageable = PageRequest.of(0, 10, Sort.by("publishedAt").descending());
        var result = pageService.listPublished(new PageFilterRequest(null, null, null, null), pageable);
        assertThat(result.getContent()).noneMatch(p -> p.id().equals(page.id()));
    }

    @Test
    void listPages_filterByStatus_returnsDraftOnly() {
        pageService.create(new CreatePageRequest("Draft One", null, null, null, null));
        var published = pageService.create(new CreatePageRequest("Published One", null, null, null, null));
        pageService.changeStatus(published.id(), new PageStatusRequest(PageStatus.PUBLISHED));

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        var result = pageService.list(new PageFilterRequest(PageStatus.DRAFT, null, null, null), pageable);
        assertThat(result.getContent()).allMatch(p -> p.status() == PageStatus.DRAFT);
        assertThat(result.getContent()).noneMatch(p -> p.id().equals(published.id()));
    }

    @Test
    void listPages_filterByQ_matchesTitleAndBody() {
        pageService.create(new CreatePageRequest("Spring Framework", null, "Great framework", null, null));
        pageService.create(new CreatePageRequest("Unrelated Page", null, null, null, null));

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        var result = pageService.list(new PageFilterRequest(null, null, null, "spring"), pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Spring Framework");
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./mvnw test -Dtest=PageServiceIT -q 2>&1 | tail -5
```

Expected: BUILD FAILURE (PageService does not exist yet)

- [ ] **Step 3: Write PageService**

```java
package io.k2dv.garden.content.service;

import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.PageStatus;
import io.k2dv.garden.content.model.SitePage;
import io.k2dv.garden.content.repository.PageRepository;
import io.k2dv.garden.content.specification.PageSpecification;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PageService {

    private final PageRepository pageRepo;

    @Transactional
    public AdminPageResponse create(CreatePageRequest req) {
        String handle = req.handle() != null ? req.handle() : slugify(req.title(), "page");
        if (pageRepo.existsByHandleAndDeletedAtIsNull(handle)) {
            throw new ConflictException("HANDLE_CONFLICT", "A page with this handle already exists");
        }
        SitePage page = new SitePage();
        page.setTitle(req.title());
        page.setHandle(handle);
        page.setBody(req.body());
        page.setMetaTitle(req.metaTitle());
        page.setMetaDescription(req.metaDescription());
        page.setStatus(PageStatus.DRAFT);
        return toAdminResponse(pageRepo.save(page));
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminPageResponse> list(PageFilterRequest filter, Pageable pageable) {
        var spec = PageSpecification.toSpec(filter);
        return PagedResult.of(pageRepo.findAll(spec, pageable).map(this::toAdminResponse));
    }

    @Transactional(readOnly = true)
    public AdminPageResponse get(UUID id) {
        return toAdminResponse(findOrThrow(id));
    }

    @Transactional
    public AdminPageResponse update(UUID id, UpdatePageRequest req) {
        SitePage page = findOrThrow(id);
        if (req.title() != null) page.setTitle(req.title());
        if (req.handle() != null) {
            if (pageRepo.existsByHandleAndDeletedAtIsNullAndIdNot(req.handle(), id)) {
                throw new ConflictException("HANDLE_CONFLICT", "A page with this handle already exists");
            }
            page.setHandle(req.handle());
        }
        if (req.body() != null) page.setBody(req.body());
        if (req.metaTitle() != null) page.setMetaTitle(req.metaTitle());
        if (req.metaDescription() != null) page.setMetaDescription(req.metaDescription());
        return toAdminResponse(page);
    }

    @Transactional
    public AdminPageResponse changeStatus(UUID id, PageStatusRequest req) {
        SitePage page = findOrThrow(id);
        page.setStatus(req.status());
        if (req.status() == PageStatus.PUBLISHED) {
            page.setPublishedAt(Instant.now());
        } else {
            page.setPublishedAt(null);
        }
        return toAdminResponse(page);
    }

    @Transactional
    public void delete(UUID id) {
        SitePage page = findOrThrow(id);
        page.setDeletedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public PageResponse getByHandle(String handle) {
        var pages = pageRepo.findAll(
            PageSpecification.publishedSpec()
                .and((root, q, cb) -> cb.equal(root.get("handle"), handle))
        );
        if (pages.isEmpty()) throw new NotFoundException("PAGE_NOT_FOUND", "Page not found");
        return toResponse(pages.get(0));
    }

    @Transactional(readOnly = true)
    public PagedResult<PageResponse> listPublished(PageFilterRequest filter, Pageable pageable) {
        // Force status = PUBLISHED and deletedAt IS NULL; merge any other filters from filter
        var spec = PageSpecification.publishedSpec();
        if (filter != null && filter.q() != null && !filter.q().isBlank()) {
            String pattern = "%" + filter.q().toLowerCase() + "%";
            spec = spec.and((root, query, cb) -> cb.or(
                cb.like(cb.lower(root.get("title")), pattern),
                cb.like(cb.lower(root.get("body")),  pattern)
            ));
        }
        return PagedResult.of(pageRepo.findAll(spec, pageable).map(this::toResponse));
    }

    // --- helpers ---

    private SitePage findOrThrow(UUID id) {
        return pageRepo.findByIdAndDeletedAtIsNull(id)
            .orElseThrow(() -> new NotFoundException("PAGE_NOT_FOUND", "Page not found"));
    }

    static String slugify(String value, String fallback) {
        if (value == null) return fallback;
        String slug = value.toLowerCase()
            .replaceAll("[^a-z0-9]+", "-")
            .replaceAll("^-+|-+$", "");
        return slug.isEmpty() ? fallback : slug;
    }

    private AdminPageResponse toAdminResponse(SitePage p) {
        return new AdminPageResponse(p.getId(), p.getTitle(), p.getHandle(), p.getBody(),
            p.getStatus(), p.getMetaTitle(), p.getMetaDescription(),
            p.getPublishedAt(), p.getCreatedAt(), p.getUpdatedAt(), p.getDeletedAt());
    }

    private PageResponse toResponse(SitePage p) {
        return new PageResponse(p.getId(), p.getTitle(), p.getHandle(), p.getBody(),
            p.getMetaTitle(), p.getMetaDescription(), p.getPublishedAt());
    }
}
```

- [ ] **Step 4: Run tests to confirm they pass**

```bash
./mvnw test -Dtest=PageServiceIT -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all 7 tests pass

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/k2dv/garden/content/service/PageService.java \
        src/test/java/io/k2dv/garden/content/service/PageServiceIT.java
git commit -m "feat(content): add PageService with filtering and integration tests"
```

---

## Task 6: ArticleService + ArticleImageService + ArticleServiceIT

**Files:**
- Create: `src/main/java/io/k2dv/garden/content/service/ArticleService.java`
- Create: `src/main/java/io/k2dv/garden/content/service/ArticleImageService.java`
- Create: `src/test/java/io/k2dv/garden/content/service/ArticleServiceIT.java`

- [ ] **Step 1: Write the failing integration tests**

```java
package io.k2dv.garden.content.service;

import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.ArticleStatus;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.model.UserStatus;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ArticleServiceIT extends AbstractIntegrationTest {

    @Autowired ArticleService articleService;
    @Autowired ArticleImageService articleImageService;
    @Autowired UserRepository userRepo;

    private AdminBlogResponse createBlog(String title) {
        return articleService.createBlog(new CreateBlogRequest(title, null));
    }

    @Test
    void createBlog_persistsBlog() {
        var blog = createBlog("Tech Blog");
        assertThat(blog.handle()).isEqualTo("tech-blog");
        assertThat(blog.title()).isEqualTo("Tech Blog");
    }

    @Test
    void createArticle_withTags_findsOrCreatesTags() {
        var blog = createBlog("Dev Blog");
        var req1 = new CreateArticleRequest("Article One", null, null, null, null, null, null, List.of("java", "spring"));
        var req2 = new CreateArticleRequest("Article Two", null, null, null, null, null, null, List.of("java"));
        var a1 = articleService.createArticle(blog.id(), req1);
        var a2 = articleService.createArticle(blog.id(), req2);

        assertThat(a1.tags()).containsExactlyInAnyOrder("java", "spring");
        assertThat(a2.tags()).containsExactly("java");
        // Both share the same ContentTag row for "java"
        // (verified by no ConflictException — UNIQUE constraint on content_tags.name)
    }

    @Test
    void createArticle_sameHandle_differentBlog_succeeds() {
        var blog1 = createBlog("Blog One");
        var blog2 = createBlog("Blog Two");
        articleService.createArticle(blog1.id(), new CreateArticleRequest("Intro", null, null, null, null, null, null, List.of()));
        var a2 = articleService.createArticle(blog2.id(), new CreateArticleRequest("Intro", null, null, null, null, null, null, List.of()));
        assertThat(a2.handle()).isEqualTo("intro");
    }

    @Test
    void createArticle_sameHandle_sameBlog_throwsConflict() {
        var blog = createBlog("My Blog");
        articleService.createArticle(blog.id(), new CreateArticleRequest("Getting Started", null, null, null, null, null, null, List.of()));
        assertThatThrownBy(() ->
            articleService.createArticle(blog.id(), new CreateArticleRequest("Getting Started", null, null, null, null, null, null, List.of()))
        ).isInstanceOf(ConflictException.class);
    }

    @Test
    void publishedArticle_visibleOnStorefront() {
        var blog = createBlog("News Blog");
        var article = articleService.createArticle(blog.id(), new CreateArticleRequest("Breaking News", null, null, null, null, null, null, List.of()));
        articleService.changeArticleStatus(blog.id(), article.id(), new ArticleStatusRequest(ArticleStatus.PUBLISHED));

        var pageable = PageRequest.of(0, 10, Sort.by("publishedAt").descending());
        var result = articleService.listPublishedArticles(blog.handle(), new ArticleFilterRequest(null, null, null, null, null, null), pageable);
        assertThat(result.getContent()).anyMatch(a -> a.id().equals(article.id()));
    }

    @Test
    void draftArticle_notVisibleOnStorefront() {
        var blog = createBlog("Draft Blog");
        var article = articleService.createArticle(blog.id(), new CreateArticleRequest("Draft Article", null, null, null, null, null, null, List.of()));

        var pageable = PageRequest.of(0, 10, Sort.by("publishedAt").descending());
        var result = articleService.listPublishedArticles(blog.handle(), new ArticleFilterRequest(null, null, null, null, null, null), pageable);
        assertThat(result.getContent()).noneMatch(a -> a.id().equals(article.id()));
    }

    @Test
    void changeStatus_toPublished_snapshotsAuthorName() {
        // Create a real user
        User author = new User();
        author.setEmail("jane@example.com");
        author.setFirstName("Jane");
        author.setLastName("Smith");
        author.setStatus(UserStatus.ACTIVE);
        author = userRepo.save(author);

        var blog = createBlog("Author Blog");
        var article = articleService.createArticle(blog.id(),
            new CreateArticleRequest("My Post", null, null, null, author.getId(), null, null, List.of()));
        articleService.changeArticleStatus(blog.id(), article.id(), new ArticleStatusRequest(ArticleStatus.PUBLISHED));

        var updated = articleService.getArticle(blog.id(), article.id());
        assertThat(updated.authorName()).isEqualTo("Jane Smith");
    }

    @Test
    void addFirstImage_setsFeaturedImageId() {
        var blog = createBlog("Photo Blog");
        var article = articleService.createArticle(blog.id(), new CreateArticleRequest("Gallery", null, null, null, null, null, null, List.of()));
        assertThat(article.featuredImageId()).isNull();

        // We can't upload a real blob in a unit test, so we test the service directly
        // by verifying the service sets featuredImageId when it was null.
        // Create a fake blob via ArticleImageService (it uses blobId directly, no upload needed)
        // Use a random UUID as blobId — ArticleImageService.addImage stores it without validating existence
        java.util.UUID fakeBlobId = java.util.UUID.randomUUID();

        // Note: ArticleImageService.addImage calls blobRepo.findById for the response URL.
        // In tests, this returns empty Optional → url = "". That is acceptable here.
        var imgResp = articleImageService.addImage(blog.id(), article.id(), new CreateArticleImageRequest(fakeBlobId, "alt"));
        var updated = articleService.getArticle(blog.id(), article.id());
        assertThat(updated.featuredImageId()).isEqualTo(imgResp.id());
    }

    @Test
    void deleteFeaturedImage_promotesNext() {
        var blog = createBlog("Media Blog");
        var article = articleService.createArticle(blog.id(), new CreateArticleRequest("Media Post", null, null, null, null, null, null, List.of()));

        var img1 = articleImageService.addImage(blog.id(), article.id(), new CreateArticleImageRequest(java.util.UUID.randomUUID(), "first"));
        var img2 = articleImageService.addImage(blog.id(), article.id(), new CreateArticleImageRequest(java.util.UUID.randomUUID(), "second"));

        var afterFirst = articleService.getArticle(blog.id(), article.id());
        assertThat(afterFirst.featuredImageId()).isEqualTo(img1.id());

        articleImageService.deleteImage(blog.id(), article.id(), img1.id());
        var afterDelete = articleService.getArticle(blog.id(), article.id());
        assertThat(afterDelete.featuredImageId()).isEqualTo(img2.id());
    }

    @Test
    void listArticles_filterByTag_returnsMatchingArticles() {
        var blog = createBlog("Tech Blog 2");
        articleService.createArticle(blog.id(), new CreateArticleRequest("Java Post", null, null, null, null, null, null, List.of("java")));
        articleService.createArticle(blog.id(), new CreateArticleRequest("Python Post", null, null, null, null, null, null, List.of("python")));

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        var result = articleService.listArticles(blog.id(), new ArticleFilterRequest(null, null, null, null, "java", null), pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Java Post");
    }

    @Test
    void listArticles_filterByAuthorId_returnsMatchingArticles() {
        User author = new User();
        author.setEmail("bob@example.com");
        author.setFirstName("Bob");
        author.setLastName("Jones");
        author.setStatus(UserStatus.ACTIVE);
        author = userRepo.save(author);

        var blog = createBlog("Author Filter Blog");
        articleService.createArticle(blog.id(), new CreateArticleRequest("By Bob", null, null, null, author.getId(), null, null, List.of()));
        articleService.createArticle(blog.id(), new CreateArticleRequest("By Nobody", null, null, null, null, null, null, List.of()));

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        var result = articleService.listArticles(blog.id(), new ArticleFilterRequest(null, null, null, author.getId(), null, null), pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("By Bob");
    }

    @Test
    void listArticles_filterByQ_matchesTitleExcerptAndBody() {
        var blog = createBlog("Search Blog");
        articleService.createArticle(blog.id(), new CreateArticleRequest("Spring Boot Guide", null, "<p>Spring is great</p>", "Learn Spring", null, null, null, List.of()));
        articleService.createArticle(blog.id(), new CreateArticleRequest("Unrelated Article", null, null, null, null, null, null, List.of()));

        var pageable = PageRequest.of(0, 10, Sort.by("createdAt").descending());
        var result = articleService.listArticles(blog.id(), new ArticleFilterRequest(null, null, null, null, null, "spring"), pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).title()).isEqualTo("Spring Boot Guide");
    }

    @Test
    void listPublishedArticles_filterByTag_excludesDraftAndUntagged() {
        var blog = createBlog("Tag Filter Blog");
        var published = articleService.createArticle(blog.id(), new CreateArticleRequest("Java Post", null, null, null, null, null, null, List.of("java")));
        articleService.changeArticleStatus(blog.id(), published.id(), new ArticleStatusRequest(ArticleStatus.PUBLISHED));
        articleService.createArticle(blog.id(), new CreateArticleRequest("Draft Java Post", null, null, null, null, null, null, List.of("java"))); // stays DRAFT

        var pageable = PageRequest.of(0, 10, Sort.by("publishedAt").descending());
        var result = articleService.listPublishedArticles(blog.handle(), new ArticleFilterRequest(null, null, null, null, "java", null), pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(published.id());
    }
}
```

- [ ] **Step 2: Run tests to confirm they fail**

```bash
./mvnw test -Dtest=ArticleServiceIT -q 2>&1 | tail -5
```

Expected: BUILD FAILURE (ArticleService / ArticleImageService don't exist)

- [ ] **Step 3: Write ArticleService**

```java
package io.k2dv.garden.content.service;

import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.*;
import io.k2dv.garden.content.repository.*;
import io.k2dv.garden.content.specification.ArticleSpecification;
import io.k2dv.garden.content.specification.BlogSpecification;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.repository.UserRepository;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ArticleService {

    private final BlogRepository blogRepo;
    private final ArticleRepository articleRepo;
    private final ArticleImageRepository imageRepo;
    private final ContentTagRepository tagRepo;
    private final UserRepository userRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    // ---- Blog operations ----

    @Transactional
    public AdminBlogResponse createBlog(CreateBlogRequest req) {
        String handle = req.handle() != null ? req.handle() : PageService.slugify(req.title(), "blog");
        if (blogRepo.existsByHandle(handle)) {
            throw new ConflictException("HANDLE_CONFLICT", "A blog with this handle already exists");
        }
        Blog blog = new Blog();
        blog.setTitle(req.title());
        blog.setHandle(handle);
        return toBlogAdminResponse(blogRepo.save(blog));
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminBlogResponse> listBlogs(BlogFilterRequest filter, Pageable pageable) {
        return PagedResult.of(blogRepo.findAll(BlogSpecification.toSpec(filter), pageable)
            .map(this::toBlogAdminResponse));
    }

    @Transactional(readOnly = true)
    public AdminBlogResponse getBlog(UUID id) {
        return toBlogAdminResponse(findBlogOrThrow(id));
    }

    @Transactional
    public AdminBlogResponse updateBlog(UUID id, UpdateBlogRequest req) {
        Blog blog = findBlogOrThrow(id);
        if (req.title() != null) blog.setTitle(req.title());
        if (req.handle() != null) {
            if (blogRepo.existsByHandleAndIdNot(req.handle(), id)) {
                throw new ConflictException("HANDLE_CONFLICT", "A blog with this handle already exists");
            }
            blog.setHandle(req.handle());
        }
        return toBlogAdminResponse(blog);
    }

    @Transactional
    public void deleteBlog(UUID id) {
        Blog blog = findBlogOrThrow(id);
        blogRepo.delete(blog);
    }

    @Transactional(readOnly = true)
    public BlogResponse getBlogByHandle(String handle) {
        Blog blog = blogRepo.findByHandle(handle)
            .orElseThrow(() -> new NotFoundException("BLOG_NOT_FOUND", "Blog not found"));
        return toBlogResponse(blog);
    }

    @Transactional(readOnly = true)
    public PagedResult<BlogResponse> listBlogsPublic(BlogFilterRequest filter, Pageable pageable) {
        // Storefront: bind only titleContains (not handleContains)
        BlogFilterRequest sfFilter = filter != null
            ? new BlogFilterRequest(filter.titleContains(), null)
            : null;
        return PagedResult.of(blogRepo.findAll(BlogSpecification.toSpec(sfFilter), pageable)
            .map(this::toBlogResponse));
    }

    // ---- Article operations ----

    @Transactional
    public AdminArticleResponse createArticle(UUID blogId, CreateArticleRequest req) {
        Blog blog = findBlogOrThrow(blogId);
        String handle = req.handle() != null ? req.handle() : PageService.slugify(req.title(), "article");
        if (articleRepo.existsByHandleAndBlogIdAndDeletedAtIsNull(handle, blogId)) {
            throw new ConflictException("HANDLE_CONFLICT", "An article with this handle already exists in this blog");
        }
        Article article = new Article();
        article.setBlogId(blogId);
        article.setTitle(req.title());
        article.setHandle(handle);
        article.setBody(req.body());
        article.setExcerpt(req.excerpt());
        article.setAuthorId(req.authorId());
        article.setMetaTitle(req.metaTitle());
        article.setMetaDescription(req.metaDescription());
        article.setStatus(ArticleStatus.DRAFT);
        if (req.tags() != null) {
            req.tags().forEach(name -> article.getTags().add(findOrCreateTag(name)));
        }
        return toArticleAdminResponse(articleRepo.save(article));
    }

    @Transactional(readOnly = true)
    public PagedResult<AdminArticleResponse> listArticles(UUID blogId, ArticleFilterRequest filter, Pageable pageable) {
        findBlogOrThrow(blogId);
        var spec = ArticleSpecification.toSpec(blogId, filter);
        var page = articleRepo.findAll(spec, pageable);
        return PagedResult.of(page.map(this::toArticleAdminResponse));
    }

    @Transactional(readOnly = true)
    public AdminArticleResponse getArticle(UUID blogId, UUID articleId) {
        findBlogOrThrow(blogId);
        Article article = articleRepo.findByIdAndBlogIdAndDeletedAtIsNull(articleId, blogId)
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));
        return toArticleAdminResponse(article);
    }

    @Transactional
    public AdminArticleResponse updateArticle(UUID blogId, UUID articleId, UpdateArticleRequest req) {
        findBlogOrThrow(blogId);
        Article article = articleRepo.findByIdAndBlogIdAndDeletedAtIsNull(articleId, blogId)
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));
        if (req.title() != null) article.setTitle(req.title());
        if (req.handle() != null) {
            if (articleRepo.existsByHandleAndBlogIdAndDeletedAtIsNullAndIdNot(req.handle(), blogId, articleId)) {
                throw new ConflictException("HANDLE_CONFLICT", "An article with this handle already exists in this blog");
            }
            article.setHandle(req.handle());
        }
        if (req.body() != null) article.setBody(req.body());
        if (req.excerpt() != null) article.setExcerpt(req.excerpt());
        if (req.authorId() != null) article.setAuthorId(req.authorId());
        if (req.metaTitle() != null) article.setMetaTitle(req.metaTitle());
        if (req.metaDescription() != null) article.setMetaDescription(req.metaDescription());
        if (req.tags() != null) {
            article.getTags().clear();
            req.tags().forEach(name -> article.getTags().add(findOrCreateTag(name)));
        }
        return toArticleAdminResponse(article);
    }

    @Transactional
    public AdminArticleResponse changeArticleStatus(UUID blogId, UUID articleId, ArticleStatusRequest req) {
        findBlogOrThrow(blogId);
        Article article = articleRepo.findByIdAndBlogIdAndDeletedAtIsNull(articleId, blogId)
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));
        article.setStatus(req.status());
        if (req.status() == ArticleStatus.PUBLISHED) {
            article.setPublishedAt(Instant.now());
            if (article.getAuthorName() == null && article.getAuthorId() != null) {
                userRepo.findById(article.getAuthorId()).ifPresent(user ->
                    article.setAuthorName(user.getFirstName() + " " + user.getLastName()));
            }
        } else {
            article.setPublishedAt(null);
        }
        return toArticleAdminResponse(article);
    }

    @Transactional
    public void deleteArticle(UUID blogId, UUID articleId) {
        findBlogOrThrow(blogId);
        Article article = articleRepo.findByIdAndBlogIdAndDeletedAtIsNull(articleId, blogId)
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));
        article.setDeletedAt(Instant.now());
    }

    @Transactional(readOnly = true)
    public ArticleResponse getArticleByHandle(String blogHandle, String articleHandle) {
        Blog blog = blogRepo.findByHandle(blogHandle)
            .orElseThrow(() -> new NotFoundException("BLOG_NOT_FOUND", "Blog not found"));
        var spec = ArticleSpecification.publishedSpec(blog.getId())
            .and((root, q, cb) -> cb.equal(root.get("handle"), articleHandle));
        Article article = articleRepo.findAll(spec).stream().findFirst()
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));
        return toArticleResponse(article);
    }

    @Transactional(readOnly = true)
    public PagedResult<ArticleResponse> listPublishedArticles(String blogHandle, ArticleFilterRequest filter, Pageable pageable) {
        Blog blog = blogRepo.findByHandle(blogHandle)
            .orElseThrow(() -> new NotFoundException("BLOG_NOT_FOUND", "Blog not found"));
        ArticleFilterRequest sfFilter = filter != null
            ? new ArticleFilterRequest(ArticleStatus.PUBLISHED, null, null, null, filter.tag(), filter.q())
            : new ArticleFilterRequest(ArticleStatus.PUBLISHED, null, null, null, null, null);
        return PagedResult.of(articleRepo.findAll(ArticleSpecification.toSpec(blog.getId(), sfFilter), pageable)
            .map(this::toArticleResponse));
    }

    // ---- Helpers ----

    private Blog findBlogOrThrow(UUID id) {
        return blogRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("BLOG_NOT_FOUND", "Blog not found"));
    }

    private ContentTag findOrCreateTag(String name) {
        return tagRepo.findByName(name).orElseGet(() -> {
            ContentTag t = new ContentTag();
            t.setName(name);
            return tagRepo.save(t);
        });
    }

    private AdminBlogResponse toBlogAdminResponse(Blog b) {
        return new AdminBlogResponse(b.getId(), b.getTitle(), b.getHandle(), b.getCreatedAt(), b.getUpdatedAt());
    }

    private BlogResponse toBlogResponse(Blog b) {
        return new BlogResponse(b.getId(), b.getTitle(), b.getHandle(), b.getCreatedAt());
    }

    private AdminArticleResponse toArticleAdminResponse(Article a) {
        List<ArticleImage> images = imageRepo.findByArticleIdOrderByPositionAsc(a.getId());
        Set<UUID> blobIds = images.stream().map(ArticleImage::getBlobId).collect(Collectors.toSet());
        Map<UUID, String> blobUrls = blobRepo.findAllById(blobIds).stream()
            .collect(Collectors.toMap(b -> b.getId(), b -> storageService.resolveUrl(b.getKey())));
        List<ArticleImageResponse> imageResponses = images.stream()
            .map(img -> new ArticleImageResponse(img.getId(),
                blobUrls.getOrDefault(img.getBlobId(), ""), img.getAltText(), img.getPosition()))
            .toList();
        List<String> tagNames = a.getTags().stream().map(ContentTag::getName).toList();
        return new AdminArticleResponse(a.getId(), a.getBlogId(), a.getTitle(), a.getHandle(),
            a.getBody(), a.getExcerpt(), a.getAuthorId(), a.getAuthorName(), a.getStatus(),
            a.getFeaturedImageId(), imageResponses, tagNames,
            a.getMetaTitle(), a.getMetaDescription(),
            a.getPublishedAt(), a.getCreatedAt(), a.getUpdatedAt(), a.getDeletedAt());
    }

    private ArticleResponse toArticleResponse(Article a) {
        List<ArticleImage> images = imageRepo.findByArticleIdOrderByPositionAsc(a.getId());
        Set<UUID> blobIds = images.stream().map(ArticleImage::getBlobId).collect(Collectors.toSet());
        Map<UUID, String> blobUrls = blobRepo.findAllById(blobIds).stream()
            .collect(Collectors.toMap(b -> b.getId(), b -> storageService.resolveUrl(b.getKey())));
        List<ArticleImageResponse> imageResponses = images.stream()
            .map(img -> new ArticleImageResponse(img.getId(),
                blobUrls.getOrDefault(img.getBlobId(), ""), img.getAltText(), img.getPosition()))
            .toList();
        List<String> tagNames = a.getTags().stream().map(ContentTag::getName).toList();
        return new ArticleResponse(a.getId(), a.getBlogId(), a.getTitle(), a.getHandle(),
            a.getBody(), a.getExcerpt(), a.getAuthorName(),
            a.getFeaturedImageId(), imageResponses, tagNames,
            a.getMetaTitle(), a.getMetaDescription(), a.getPublishedAt());
    }
}
```

- [ ] **Step 4: Write ArticleImageService**

```java
package io.k2dv.garden.content.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.content.dto.ArticleImagePositionItem;
import io.k2dv.garden.content.dto.ArticleImageResponse;
import io.k2dv.garden.content.dto.CreateArticleImageRequest;
import io.k2dv.garden.content.model.Article;
import io.k2dv.garden.content.model.ArticleImage;
import io.k2dv.garden.content.repository.ArticleImageRepository;
import io.k2dv.garden.content.repository.ArticleRepository;
import io.k2dv.garden.content.repository.BlogRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ArticleImageService {

    private final ArticleImageRepository imageRepo;
    private final ArticleRepository articleRepo;
    private final BlogRepository blogRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    @Transactional
    public ArticleImageResponse addImage(UUID blogId, UUID articleId, CreateArticleImageRequest req) {
        verifyBlogExists(blogId);
        Article article = articleRepo.findByIdAndBlogIdAndDeletedAtIsNull(articleId, blogId)
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));

        int nextPosition = imageRepo.countByArticleId(articleId) + 1;
        ArticleImage img = new ArticleImage();
        img.setArticleId(articleId);
        img.setBlobId(req.blobId());
        img.setAltText(req.altText());
        img.setPosition(nextPosition);
        img = imageRepo.save(img);

        if (article.getFeaturedImageId() == null) {
            article.setFeaturedImageId(img.getId());
        }

        return toResponse(img);
    }

    @Transactional
    public void deleteImage(UUID blogId, UUID articleId, UUID imageId) {
        verifyBlogExists(blogId);
        Article article = articleRepo.findByIdAndBlogIdAndDeletedAtIsNull(articleId, blogId)
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));
        ArticleImage img = imageRepo.findById(imageId)
            .filter(i -> i.getArticleId().equals(articleId))
            .orElseThrow(() -> new NotFoundException("IMAGE_NOT_FOUND", "Image not found"));

        boolean wasFeatured = imageId.equals(article.getFeaturedImageId());
        imageRepo.delete(img);

        if (wasFeatured) {
            List<ArticleImage> remaining = imageRepo.findByArticleIdOrderByPositionAsc(articleId);
            article.setFeaturedImageId(remaining.isEmpty() ? null : remaining.get(0).getId());
        }
    }

    @Transactional
    public void reorderImages(UUID blogId, UUID articleId, List<ArticleImagePositionItem> items) {
        verifyBlogExists(blogId);
        articleRepo.findByIdAndBlogIdAndDeletedAtIsNull(articleId, blogId)
            .orElseThrow(() -> new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));
        for (ArticleImagePositionItem item : items) {
            imageRepo.findById(item.id())
                .filter(i -> i.getArticleId().equals(articleId))
                .ifPresent(i -> i.setPosition(item.position()));
        }
    }

    private void verifyBlogExists(UUID blogId) {
        if (!blogRepo.existsById(blogId)) {
            throw new NotFoundException("BLOG_NOT_FOUND", "Blog not found");
        }
    }

    private ArticleImageResponse toResponse(ArticleImage img) {
        String url = blobRepo.findById(img.getBlobId())
            .map(b -> storageService.resolveUrl(b.getKey()))
            .orElse("");
        return new ArticleImageResponse(img.getId(), url, img.getAltText(), img.getPosition());
    }
}
```

- [ ] **Step 5: Run tests to confirm they pass**

```bash
./mvnw test -Dtest=ArticleServiceIT -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all 13 tests pass

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/k2dv/garden/content/service/ \
        src/test/java/io/k2dv/garden/content/service/ArticleServiceIT.java
git commit -m "feat(content): add ArticleService, ArticleImageService, and integration tests"
```

---

## Task 7: Admin Controllers

**Files:**
- Create: `src/main/java/io/k2dv/garden/content/controller/AdminPageController.java`
- Create: `src/main/java/io/k2dv/garden/content/controller/AdminBlogController.java`

- [ ] **Step 1: Write AdminPageController**

```java
package io.k2dv.garden.content.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.PageStatus;
import io.k2dv.garden.content.service.PageService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/pages")
@RequiredArgsConstructor
public class AdminPageController {

    private final PageService pageService;

    @PostMapping
    @HasPermission("content:write")
    public ResponseEntity<ApiResponse<AdminPageResponse>> create(@Valid @RequestBody CreatePageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(pageService.create(req)));
    }

    @GetMapping
    @HasPermission("content:read")
    public ResponseEntity<ApiResponse<PagedResult<AdminPageResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) PageStatus status,
            @RequestParam(required = false) String titleContains,
            @RequestParam(required = false) String handleContains,
            @RequestParam(required = false) String q) {
        var filter = new PageFilterRequest(status, titleContains, handleContains, q);
        var pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.of(pageService.list(filter, pageable)));
    }

    @GetMapping("/{id}")
    @HasPermission("content:read")
    public ResponseEntity<ApiResponse<AdminPageResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(pageService.get(id)));
    }

    @PutMapping("/{id}")
    @HasPermission("content:write")
    public ResponseEntity<ApiResponse<AdminPageResponse>> update(
            @PathVariable UUID id, @RequestBody UpdatePageRequest req) {
        return ResponseEntity.ok(ApiResponse.of(pageService.update(id, req)));
    }

    @PatchMapping("/{id}/status")
    @HasPermission("content:publish")
    public ResponseEntity<ApiResponse<AdminPageResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody PageStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.of(pageService.changeStatus(id, req)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("content:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        pageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 2: Write AdminBlogController**

```java
package io.k2dv.garden.content.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.ArticleStatus;
import io.k2dv.garden.content.service.ArticleImageService;
import io.k2dv.garden.content.service.ArticleService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/blogs")
@RequiredArgsConstructor
public class AdminBlogController {

    private final ArticleService articleService;
    private final ArticleImageService articleImageService;

    // --- Blogs ---

    @PostMapping
    @HasPermission("content:write")
    public ResponseEntity<ApiResponse<AdminBlogResponse>> createBlog(@Valid @RequestBody CreateBlogRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(articleService.createBlog(req)));
    }

    @GetMapping
    @HasPermission("content:read")
    public ResponseEntity<ApiResponse<PagedResult<AdminBlogResponse>>> listBlogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String titleContains,
            @RequestParam(required = false) String handleContains) {
        var filter = new BlogFilterRequest(titleContains, handleContains);
        var pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.of(articleService.listBlogs(filter, pageable)));
    }

    @GetMapping("/{id}")
    @HasPermission("content:read")
    public ResponseEntity<ApiResponse<AdminBlogResponse>> getBlog(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(articleService.getBlog(id)));
    }

    @PutMapping("/{id}")
    @HasPermission("content:write")
    public ResponseEntity<ApiResponse<AdminBlogResponse>> updateBlog(
            @PathVariable UUID id, @RequestBody UpdateBlogRequest req) {
        return ResponseEntity.ok(ApiResponse.of(articleService.updateBlog(id, req)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("content:delete")
    public ResponseEntity<Void> deleteBlog(@PathVariable UUID id) {
        articleService.deleteBlog(id);
        return ResponseEntity.noContent().build();
    }

    // --- Articles ---

    @PostMapping("/{id}/articles")
    @HasPermission("content:write")
    public ResponseEntity<ApiResponse<AdminArticleResponse>> createArticle(
            @PathVariable UUID id, @Valid @RequestBody CreateArticleRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(articleService.createArticle(id, req)));
    }

    @GetMapping("/{id}/articles")
    @HasPermission("content:read")
    public ResponseEntity<ApiResponse<PagedResult<AdminArticleResponse>>> listArticles(
            @PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) ArticleStatus status,
            @RequestParam(required = false) String titleContains,
            @RequestParam(required = false) String handleContains,
            @RequestParam(required = false) UUID authorId,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q) {
        var filter = new ArticleFilterRequest(status, titleContains, handleContains, authorId, tag, q);
        var pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.of(articleService.listArticles(id, filter, pageable)));
    }

    @GetMapping("/{id}/articles/{articleId}")
    @HasPermission("content:read")
    public ResponseEntity<ApiResponse<AdminArticleResponse>> getArticle(
            @PathVariable UUID id, @PathVariable UUID articleId) {
        return ResponseEntity.ok(ApiResponse.of(articleService.getArticle(id, articleId)));
    }

    @PutMapping("/{id}/articles/{articleId}")
    @HasPermission("content:write")
    public ResponseEntity<ApiResponse<AdminArticleResponse>> updateArticle(
            @PathVariable UUID id, @PathVariable UUID articleId,
            @RequestBody UpdateArticleRequest req) {
        return ResponseEntity.ok(ApiResponse.of(articleService.updateArticle(id, articleId, req)));
    }

    @PatchMapping("/{id}/articles/{articleId}/status")
    @HasPermission("content:publish")
    public ResponseEntity<ApiResponse<AdminArticleResponse>> changeArticleStatus(
            @PathVariable UUID id, @PathVariable UUID articleId,
            @Valid @RequestBody ArticleStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.of(articleService.changeArticleStatus(id, articleId, req)));
    }

    @DeleteMapping("/{id}/articles/{articleId}")
    @HasPermission("content:delete")
    public ResponseEntity<Void> deleteArticle(@PathVariable UUID id, @PathVariable UUID articleId) {
        articleService.deleteArticle(id, articleId);
        return ResponseEntity.noContent().build();
    }

    // --- Article Images — /positions MUST be declared before /{imageId} ---

    @PostMapping("/{id}/articles/{articleId}/images")
    @HasPermission("content:write")
    public ResponseEntity<ApiResponse<ArticleImageResponse>> addImage(
            @PathVariable UUID id, @PathVariable UUID articleId,
            @Valid @RequestBody CreateArticleImageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(articleImageService.addImage(id, articleId, req)));
    }

    @PatchMapping("/{id}/articles/{articleId}/images/positions")
    @HasPermission("content:write")
    public ResponseEntity<Void> reorderImages(
            @PathVariable UUID id, @PathVariable UUID articleId,
            @RequestBody List<ArticleImagePositionItem> items) {
        articleImageService.reorderImages(id, articleId, items);
        return ResponseEntity.ok().build();
    }

    @DeleteMapping("/{id}/articles/{articleId}/images/{imageId}")
    @HasPermission("content:delete")
    public ResponseEntity<Void> deleteImage(
            @PathVariable UUID id, @PathVariable UUID articleId, @PathVariable UUID imageId) {
        articleImageService.deleteImage(id, articleId, imageId);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/k2dv/garden/content/controller/AdminPageController.java \
        src/main/java/io/k2dv/garden/content/controller/AdminBlogController.java
git commit -m "feat(content): add AdminPageController and AdminBlogController"
```

---

## Task 8: Storefront Controllers

**Files:**
- Create: `src/main/java/io/k2dv/garden/content/controller/StorefrontPageController.java`
- Create: `src/main/java/io/k2dv/garden/content/controller/StorefrontBlogController.java`

- [ ] **Step 1: Write StorefrontPageController**

```java
package io.k2dv.garden.content.controller;

import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.service.PageService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pages")
@RequiredArgsConstructor
public class StorefrontPageController {

    private final PageService pageService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<PageResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String q) {
        var filter = new PageFilterRequest(null, null, null, q);
        var pageable = PageRequest.of(page, pageSize, Sort.by("publishedAt").descending());
        return ResponseEntity.ok(ApiResponse.of(pageService.listPublished(filter, pageable)));
    }

    @GetMapping("/{handle}")
    public ResponseEntity<ApiResponse<PageResponse>> getByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.of(pageService.getByHandle(handle)));
    }
}
```

- [ ] **Step 2: Write StorefrontBlogController**

```java
package io.k2dv.garden.content.controller;

import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.ArticleStatus;
import io.k2dv.garden.content.service.ArticleService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/blogs")
@RequiredArgsConstructor
public class StorefrontBlogController {

    private final ArticleService articleService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<BlogResponse>>> listBlogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String titleContains) {
        var filter = new BlogFilterRequest(titleContains, null);
        var pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.of(articleService.listBlogsPublic(filter, pageable)));
    }

    @GetMapping("/{blogHandle}")
    public ResponseEntity<ApiResponse<BlogResponse>> getBlog(@PathVariable String blogHandle) {
        return ResponseEntity.ok(ApiResponse.of(articleService.getBlogByHandle(blogHandle)));
    }

    @GetMapping("/{blogHandle}/articles")
    public ResponseEntity<ApiResponse<PagedResult<ArticleResponse>>> listArticles(
            @PathVariable String blogHandle,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String q) {
        // status forced to PUBLISHED in service; storefront only exposes tag + q
        var filter = new ArticleFilterRequest(null, null, null, null, tag, q);
        var pageable = PageRequest.of(page, pageSize, Sort.by("publishedAt").descending());
        return ResponseEntity.ok(ApiResponse.of(articleService.listPublishedArticles(blogHandle, filter, pageable)));
    }

    @GetMapping("/{blogHandle}/articles/{articleHandle}")
    public ResponseEntity<ApiResponse<ArticleResponse>> getArticle(
            @PathVariable String blogHandle, @PathVariable String articleHandle) {
        return ResponseEntity.ok(ApiResponse.of(articleService.getArticleByHandle(blogHandle, articleHandle)));
    }
}
```

- [ ] **Step 3: Verify compilation**

```bash
./mvnw compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/k2dv/garden/content/controller/StorefrontPageController.java \
        src/main/java/io/k2dv/garden/content/controller/StorefrontBlogController.java
git commit -m "feat(content): add storefront page and blog controllers"
```

---

## Task 9: Controller Slice Tests

**Files:**
- Create: `src/test/java/io/k2dv/garden/content/controller/AdminPageControllerTest.java`
- Create: `src/test/java/io/k2dv/garden/content/controller/AdminBlogControllerTest.java`
- Create: `src/test/java/io/k2dv/garden/content/controller/StorefrontContentControllerTest.java`

- [ ] **Step 1: Write AdminPageControllerTest**

```java
package io.k2dv.garden.content.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.PageStatus;
import io.k2dv.garden.content.service.PageService;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminPageController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminPageControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean PageService pageService;

    private AdminPageResponse stubPage() {
        return new AdminPageResponse(UUID.randomUUID(), "About Us", "about-us", null,
            PageStatus.DRAFT, null, null, null, null, null, null);
    }

    @Test
    void createPage_returns201() throws Exception {
        when(pageService.create(any())).thenReturn(stubPage());

        mvc.perform(post("/api/v1/admin/pages")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreatePageRequest("About Us", null, null, null, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("About Us"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void createPage_blankTitle_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/pages")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getPage_notFound_returns404() throws Exception {
        when(pageService.get(any())).thenThrow(new NotFoundException("PAGE_NOT_FOUND", "Page not found"));

        mvc.perform(get("/api/v1/admin/pages/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("PAGE_NOT_FOUND"));
    }

    @Test
    void changeStatus_returns200() throws Exception {
        when(pageService.changeStatus(any(), any())).thenReturn(stubPage());

        mvc.perform(patch("/api/v1/admin/pages/{id}/status", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"PUBLISHED\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void deletePage_returns204() throws Exception {
        doNothing().when(pageService).delete(any());

        mvc.perform(delete("/api/v1/admin/pages/{id}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void listPages_withStatusFilter_returns200() throws Exception {
        var result = new PagedResult<>(List.of(stubPage()),
            PageMeta.builder().page(0).pageSize(10).total(1L).build());
        when(pageService.list(any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/admin/pages").param("status", "DRAFT"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }
}
```

- [ ] **Step 2: Write AdminBlogControllerTest**

```java
package io.k2dv.garden.content.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.ArticleStatus;
import io.k2dv.garden.content.service.ArticleImageService;
import io.k2dv.garden.content.service.ArticleService;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminBlogController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminBlogControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean ArticleService articleService;
    @MockitoBean ArticleImageService articleImageService;

    private AdminArticleResponse stubArticle() {
        return new AdminArticleResponse(UUID.randomUUID(), UUID.randomUUID(), "My Article", "my-article",
            null, null, null, null, ArticleStatus.DRAFT, null, List.of(), List.of(),
            null, null, null, null, null, null);
    }

    @Test
    void createArticle_returns201() throws Exception {
        when(articleService.createArticle(any(), any())).thenReturn(stubArticle());

        mvc.perform(post("/api/v1/admin/blogs/{id}/articles", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateArticleRequest("My Article", null, null, null, null, null, null, List.of()))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("My Article"));
    }

    @Test
    void createArticle_blankTitle_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/blogs/{id}/articles", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getArticle_returns200() throws Exception {
        when(articleService.getArticle(any(), any())).thenReturn(stubArticle());

        mvc.perform(get("/api/v1/admin/blogs/{id}/articles/{articleId}", UUID.randomUUID(), UUID.randomUUID()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.handle").value("my-article"));
    }

    @Test
    void deleteArticle_returns204() throws Exception {
        doNothing().when(articleService).deleteArticle(any(), any());

        mvc.perform(delete("/api/v1/admin/blogs/{id}/articles/{articleId}", UUID.randomUUID(), UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void addImage_returns201() throws Exception {
        var imgResp = new ArticleImageResponse(UUID.randomUUID(), "http://cdn/img.jpg", "alt", 1);
        when(articleImageService.addImage(any(), any(), any())).thenReturn(imgResp);

        mvc.perform(post("/api/v1/admin/blogs/{id}/articles/{articleId}/images", UUID.randomUUID(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateArticleImageRequest(UUID.randomUUID(), "alt"))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.url").value("http://cdn/img.jpg"));
    }

    @Test
    void reorderImages_routingBeforeImageId_returns200() throws Exception {
        doNothing().when(articleImageService).reorderImages(any(), any(), any());

        mvc.perform(patch("/api/v1/admin/blogs/{id}/articles/{articleId}/images/positions",
                    UUID.randomUUID(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
            .andExpect(status().isOk());
    }

    @Test
    void listArticles_withTagFilter_returns200() throws Exception {
        var result = new PagedResult<>(List.of(stubArticle()),
            PageMeta.builder().page(0).pageSize(10).total(1L).build());
        when(articleService.listArticles(any(), any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/admin/blogs/{id}/articles", UUID.randomUUID()).param("tag", "java"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }
}
```

- [ ] **Step 3: Write StorefrontContentControllerTest**

```java
package io.k2dv.garden.content.controller;

import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.service.ArticleService;
import io.k2dv.garden.content.service.PageService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Tests both StorefrontPageController and StorefrontBlogController
@WebMvcTest(controllers = {StorefrontPageController.class, StorefrontBlogController.class})
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class StorefrontContentControllerTest {

    @Autowired MockMvc mvc;

    @MockitoBean PageService pageService;
    @MockitoBean ArticleService articleService;

    private PageResponse stubPublishedPage() {
        return new PageResponse(UUID.randomUUID(), "About", "about", null, null, null, Instant.now());
    }

    private ArticleResponse stubPublishedArticle() {
        return new ArticleResponse(UUID.randomUUID(), UUID.randomUUID(), "First Post", "first-post",
            null, null, null, null, List.of(), List.of(), null, null, Instant.now());
    }

    @Test
    void getPage_published_returns200() throws Exception {
        when(pageService.getByHandle("about")).thenReturn(stubPublishedPage());

        mvc.perform(get("/api/v1/pages/about"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.handle").value("about"));
    }

    @Test
    void getPage_draft_returns404() throws Exception {
        when(pageService.getByHandle("draft")).thenThrow(new NotFoundException("PAGE_NOT_FOUND", "Page not found"));

        mvc.perform(get("/api/v1/pages/draft"))
            .andExpect(status().isNotFound());
    }

    @Test
    void getArticle_published_returns200() throws Exception {
        when(articleService.getArticleByHandle("tech", "first-post")).thenReturn(stubPublishedArticle());

        mvc.perform(get("/api/v1/blogs/tech/articles/first-post"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.handle").value("first-post"));
    }

    @Test
    void getArticle_draft_returns404() throws Exception {
        when(articleService.getArticleByHandle(any(), any()))
            .thenThrow(new NotFoundException("ARTICLE_NOT_FOUND", "Article not found"));

        mvc.perform(get("/api/v1/blogs/tech/articles/draft-post"))
            .andExpect(status().isNotFound());
    }

    @Test
    void listArticles_withTagFilter_returns200() throws Exception {
        var result = new PagedResult<>(List.of(stubPublishedArticle()),
            PageMeta.builder().page(0).pageSize(10).total(1L).build());
        when(articleService.listPublishedArticles(any(), any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/blogs/tech/articles").param("tag", "java"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray());
    }
}
```

- [ ] **Step 4: Run all controller slice tests**

```bash
./mvnw test -Dtest="AdminPageControllerTest,AdminBlogControllerTest,StorefrontContentControllerTest" -q 2>&1 | tail -10
```

Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 5: Commit**

```bash
git add src/test/java/io/k2dv/garden/content/controller/
git commit -m "feat(content): add controller slice tests"
```

---

## Task 10: Final Verification

- [ ] **Step 1: Run the full test suite**

```bash
./mvnw test -q 2>&1 | tail -20
```

Expected: BUILD SUCCESS. All existing tests still pass; new content tests pass. The output should show all test classes including `PageServiceIT`, `ArticleServiceIT`, `AdminPageControllerTest`, `AdminBlogControllerTest`, `StorefrontContentControllerTest`.

- [ ] **Step 2: Verify migration count**

```bash
./mvnw test -Dtest=GardenApplicationTests -q 2>&1 | tail -5
```

Expected: BUILD SUCCESS (Flyway applies V1–V10 cleanly in sequence)

- [ ] **Step 3: Final commit**

```bash
git add -A
git status  # verify only expected files; no secrets or generated files
git commit -m "feat(content): complete content domain — pages, blogs, articles, images, tags"
```
