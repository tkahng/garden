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
