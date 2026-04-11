package io.k2dv.garden.content.controller;

import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.service.ArticleService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/blogs")
@RequiredArgsConstructor
@Tag(name = "Content", description = "Public storefront blog and page content")
@SecurityRequirements({})
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
