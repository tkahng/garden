package io.k2dv.garden.content.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.ArticleStatus;
import io.k2dv.garden.content.service.ArticleImageService;
import io.k2dv.garden.content.service.ArticleService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Content", description = "Admin blog article management")
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
