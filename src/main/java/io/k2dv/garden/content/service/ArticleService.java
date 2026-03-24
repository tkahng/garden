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
import org.springframework.data.domain.Page;
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
        Page<Blog> blogs = blogRepo.findAll(BlogSpecification.toSpec(filter), pageable);
        return PagedResult.of(blogs, this::toBlogAdminResponse);
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
        BlogFilterRequest sfFilter = filter != null
            ? new BlogFilterRequest(filter.titleContains(), null)
            : null;
        Page<Blog> blogs = blogRepo.findAll(BlogSpecification.toSpec(sfFilter), pageable);
        return PagedResult.of(blogs, this::toBlogResponse);
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
        Page<Article> page = articleRepo.findAll(spec, pageable);
        return PagedResult.of(page, this::toArticleAdminResponse);
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
        Page<Article> articles = articleRepo.findAll(ArticleSpecification.toSpec(blog.getId(), sfFilter), pageable);
        return PagedResult.of(articles, this::toArticleResponse);
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
