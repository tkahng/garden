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
