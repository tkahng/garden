package io.k2dv.garden.content.controller;

import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.service.ArticleService;
import io.k2dv.garden.content.service.PageService;
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
