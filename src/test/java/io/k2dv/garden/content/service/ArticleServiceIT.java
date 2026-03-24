package io.k2dv.garden.content.service;

import io.k2dv.garden.blob.model.BlobObject;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
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
    @Autowired BlobObjectRepository blobRepo;

    private AdminBlogResponse createBlog(String title) {
        return articleService.createBlog(new CreateBlogRequest(title, null));
    }

    private java.util.UUID createBlob(String key) {
        BlobObject blob = new BlobObject();
        blob.setKey(key);
        blob.setFilename(key);
        blob.setContentType("image/jpeg");
        blob.setSize(1024L);
        return blobRepo.save(blob).getId();
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

        java.util.UUID blobId = createBlob("photo-blob-1");
        var imgResp = articleImageService.addImage(blog.id(), article.id(), new CreateArticleImageRequest(blobId, "alt"));
        var updated = articleService.getArticle(blog.id(), article.id());
        assertThat(updated.featuredImageId()).isEqualTo(imgResp.id());
    }

    @Test
    void deleteFeaturedImage_promotesNext() {
        var blog = createBlog("Media Blog");
        var article = articleService.createArticle(blog.id(), new CreateArticleRequest("Media Post", null, null, null, null, null, null, List.of()));

        var img1 = articleImageService.addImage(blog.id(), article.id(), new CreateArticleImageRequest(createBlob("media-blob-1"), "first"));
        var img2 = articleImageService.addImage(blog.id(), article.id(), new CreateArticleImageRequest(createBlob("media-blob-2"), "second"));

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
        articleService.createArticle(blog.id(), new CreateArticleRequest("Draft Java Post", null, null, null, null, null, null, List.of("java")));

        var pageable = PageRequest.of(0, 10, Sort.by("publishedAt").descending());
        var result = articleService.listPublishedArticles(blog.handle(), new ArticleFilterRequest(null, null, null, null, "java", null), pageable);
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).id()).isEqualTo(published.id());
    }
}
