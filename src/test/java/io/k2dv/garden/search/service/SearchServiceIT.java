package io.k2dv.garden.search.service;

import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.collection.model.Collection;
import io.k2dv.garden.collection.model.CollectionStatus;
import io.k2dv.garden.collection.model.CollectionType;
import io.k2dv.garden.collection.repository.CollectionRepository;
import io.k2dv.garden.content.model.Article;
import io.k2dv.garden.content.model.ArticleStatus;
import io.k2dv.garden.content.model.Blog;
import io.k2dv.garden.content.model.PageStatus;
import io.k2dv.garden.content.model.SitePage;
import io.k2dv.garden.content.repository.ArticleRepository;
import io.k2dv.garden.content.repository.BlogRepository;
import io.k2dv.garden.content.repository.PageRepository;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.ProductStatusRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.search.dto.SearchResponse;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class SearchServiceIT extends AbstractIntegrationTest {

    @Autowired SearchService searchService;
    @Autowired ProductService productService;
    @Autowired CollectionRepository collectionRepo;
    @Autowired BlogRepository blogRepo;
    @Autowired ArticleRepository articleRepo;
    @Autowired PageRepository pageRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);
    private static final Set<String> ALL_TYPES = Set.of("products", "collections", "articles", "pages");

    private String handle(String base) {
        return base + "-" + counter.incrementAndGet();
    }

    private AdminProductResponse activeProduct(String title) {
        AdminProductResponse p = productService.create(
                new CreateProductRequest(title, null, null, null, null, List.of(), null, null));
        productService.changeStatus(p.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        return p;
    }

    private Collection activeCollection(String title) {
        Collection c = new Collection();
        c.setTitle(title);
        c.setHandle(handle("col"));
        c.setCollectionType(CollectionType.MANUAL);
        c.setStatus(CollectionStatus.ACTIVE);
        return collectionRepo.save(c);
    }

    private Blog savedBlog() {
        Blog b = new Blog();
        b.setTitle("Garden Blog");
        b.setHandle(handle("blog"));
        return blogRepo.save(b);
    }

    private Article publishedArticle(Blog blog, String title) {
        Article a = new Article();
        a.setBlogId(blog.getId());
        a.setTitle(title);
        a.setHandle(handle("article"));
        a.setStatus(ArticleStatus.PUBLISHED);
        a.setPublishedAt(Instant.now());
        return articleRepo.save(a);
    }

    private SitePage publishedPage(String title) {
        SitePage p = new SitePage();
        p.setTitle(title);
        p.setHandle(handle("page"));
        p.setStatus(PageStatus.PUBLISHED);
        p.setPublishedAt(Instant.now());
        return pageRepo.save(p);
    }

    @Test
    void search_findsActiveProductsByTitle() {
        activeProduct("Shovel Pro 3000");

        SearchResponse result = searchService.search("shovel pro 3000", ALL_TYPES, PageRequest.of(0, 10));

        assertThat(result.products().getContent())
                .anyMatch(p -> p.title().equals("Shovel Pro 3000"));
    }

    @Test
    void search_excludesDraftProducts() {
        productService.create(
                new CreateProductRequest("Draft Rake Unique", null, null, null, null, List.of(), null, null));

        SearchResponse result = searchService.search("draft rake unique", ALL_TYPES, PageRequest.of(0, 10));

        assertThat(result.products().getContent())
                .noneMatch(p -> p.title().equals("Draft Rake Unique"));
    }

    @Test
    void search_findsActiveCollectionsByTitle() {
        activeCollection("Premium Garden Rakes");

        SearchResponse result = searchService.search("premium garden rakes", ALL_TYPES, PageRequest.of(0, 10));

        assertThat(result.collections().getContent())
                .anyMatch(c -> c.title().equals("Premium Garden Rakes"));
    }

    @Test
    void search_excludesDraftCollections() {
        Collection draft = new Collection();
        draft.setTitle("Hidden Collection Unique");
        draft.setHandle(handle("col"));
        draft.setCollectionType(CollectionType.MANUAL);
        draft.setStatus(CollectionStatus.DRAFT);
        collectionRepo.save(draft);

        SearchResponse result = searchService.search("hidden collection unique", ALL_TYPES, PageRequest.of(0, 10));

        assertThat(result.collections().getContent())
                .noneMatch(c -> c.title().equals("Hidden Collection Unique"));
    }

    @Test
    void search_findsPublishedArticlesByTitle() {
        Blog blog = savedBlog();
        publishedArticle(blog, "Watering Techniques For Beginners");

        SearchResponse result = searchService.search("watering techniques for beginners", ALL_TYPES, PageRequest.of(0, 10));

        assertThat(result.articles().getContent())
                .anyMatch(a -> a.title().equals("Watering Techniques For Beginners"));
    }

    @Test
    void search_findsPublishedPagesByTitle() {
        publishedPage("Soil Preparation Guide");

        SearchResponse result = searchService.search("soil preparation guide", ALL_TYPES, PageRequest.of(0, 10));

        assertThat(result.pages().getContent())
                .anyMatch(p -> p.title().equals("Soil Preparation Guide"));
    }

    @Test
    void search_typeFilter_onlySearchesRequestedTypes() {
        activeProduct("FilterXYZ Product");
        activeCollection("FilterXYZ Collection");

        SearchResponse result = searchService.search("filterxyz", Set.of("products"), PageRequest.of(0, 10));

        assertThat(result.products().getContent()).isNotEmpty();
        assertThat(result.collections()).isNull();
        assertThat(result.articles()).isNull();
        assertThat(result.pages()).isNull();
    }

    @Test
    void search_noMatches_returnsEmptyPagedResults() {
        SearchResponse result = searchService.search("xyznonexistentterm99887766", ALL_TYPES, PageRequest.of(0, 10));

        assertThat(result.products().getContent()).isEmpty();
        assertThat(result.collections().getContent()).isEmpty();
        assertThat(result.articles().getContent()).isEmpty();
        assertThat(result.pages().getContent()).isEmpty();
    }
}
