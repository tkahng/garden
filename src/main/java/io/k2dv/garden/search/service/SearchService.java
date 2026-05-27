package io.k2dv.garden.search.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.collection.dto.response.CollectionSummaryResponse;
import io.k2dv.garden.collection.model.Collection;
import io.k2dv.garden.collection.repository.CollectionRepository;
import io.k2dv.garden.content.model.Article;
import io.k2dv.garden.content.model.Blog;
import io.k2dv.garden.content.model.SitePage;
import io.k2dv.garden.content.repository.ArticleRepository;
import io.k2dv.garden.content.repository.BlogRepository;
import io.k2dv.garden.content.repository.PageRepository;
import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.product.service.ProductImageResolver;
import io.k2dv.garden.search.dto.SearchArticleResult;
import io.k2dv.garden.search.dto.SearchPageResult;
import io.k2dv.garden.search.dto.SearchResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Unified search service that queries products, collections, articles, and static pages
 * in a single request using database full-text search.
 * Callers specify which content types to include via the {@code types} filter so that
 * unused result buckets are not fetched.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final ProductRepository productRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductImageResolver imageResolver;
    private final CollectionRepository collectionRepo;
    private final ArticleRepository articleRepo;
    private final BlogRepository blogRepo;
    private final PageRepository pageRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    /**
     * Executes a cross-entity search against the requested content types and returns
     * a composite response where each bucket is {@code null} when its type was not requested.
     */
    @Transactional(readOnly = true)
    public SearchResponse search(String q, Set<String> types, Pageable pageable) {
        String term = q.trim();

        PagedResult<ProductSummaryResponse> products = null;
        PagedResult<CollectionSummaryResponse> collections = null;
        PagedResult<SearchArticleResult> articles = null;
        PagedResult<SearchPageResult> pages = null;

        if (types.contains("products")) {
            products = searchProducts(term, pageable);
        }
        if (types.contains("collections")) {
            collections = searchCollections(term, pageable);
        }
        if (types.contains("articles")) {
            articles = searchArticles(term, pageable);
        }
        if (types.contains("pages")) {
            pages = searchPages(term, pageable);
        }

        return new SearchResponse(products, collections, articles, pages);
    }

    private PagedResult<ProductSummaryResponse> searchProducts(String term, Pageable pageable) {
        Page<Product> page = productRepo.fullTextSearch(term, pageable);
        List<Product> products = page.getContent();

        Set<UUID> productIds = products.stream().map(Product::getId).collect(Collectors.toSet());
        Map<UUID, List<ProductVariant>> variantsByProduct = productIds.isEmpty() ? Map.of() :
            variantRepo.findByProductIdInAndDeletedAtIsNull(productIds).stream()
                .collect(Collectors.groupingBy(ProductVariant::getProductId));

        Map<UUID, String> imageUrlByProductId = imageResolver.resolveByProductId(products);

        return PagedResult.of(page, p -> {
            List<ProductVariant> variants = variantsByProduct.getOrDefault(p.getId(), List.of());
            BigDecimal priceMin = variants.stream().map(ProductVariant::getPrice)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            BigDecimal priceMax = variants.stream().map(ProductVariant::getPrice)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            BigDecimal compareAtPriceMin = variants.stream().map(ProductVariant::getCompareAtPrice)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            BigDecimal compareAtPriceMax = variants.stream().map(ProductVariant::getCompareAtPrice)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            String imageUrl = imageUrlByProductId.get(p.getId());
            return new ProductSummaryResponse(p.getId(), p.getTitle(), p.getHandle(), p.getVendor(),
                imageUrl, priceMin, priceMax, compareAtPriceMin, compareAtPriceMax);
        });
    }

    private PagedResult<CollectionSummaryResponse> searchCollections(String term, Pageable pageable) {
        Page<Collection> page = collectionRepo.fullTextSearch(term, pageable);
        Set<UUID> imageIds = page.getContent().stream()
            .map(Collection::getFeaturedImageId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, String> imageUrls = imageIds.isEmpty() ? Map.of() :
            blobRepo.findAllById(imageIds).stream()
                .collect(Collectors.toMap(b -> b.getId(), b -> storageService.resolveUrl(b.getKey())));

        return PagedResult.of(page, c -> new CollectionSummaryResponse(c.getId(), c.getTitle(), c.getHandle(),
            c.getFeaturedImageId() != null ? imageUrls.get(c.getFeaturedImageId()) : null));
    }

    private PagedResult<SearchArticleResult> searchArticles(String term, Pageable pageable) {
        Page<Article> page = articleRepo.fullTextSearch(term, pageable);
        Set<UUID> blogIds = page.getContent().stream().map(Article::getBlogId).collect(Collectors.toSet());
        Map<UUID, String> blogHandleById = blogIds.isEmpty() ? Map.of() :
            blogRepo.findAllById(blogIds).stream()
                .collect(Collectors.toMap(Blog::getId, Blog::getHandle));

        return PagedResult.of(page, a -> new SearchArticleResult(
            a.getId(), a.getBlogId(), blogHandleById.get(a.getBlogId()),
            a.getTitle(), a.getHandle(), a.getExcerpt(), a.getPublishedAt()));
    }

    private PagedResult<SearchPageResult> searchPages(String term, Pageable pageable) {
        Page<SitePage> page = pageRepo.fullTextSearch(term, pageable);
        return PagedResult.of(page, p -> new SearchPageResult(p.getId(), p.getTitle(), p.getHandle(), p.getPublishedAt()));
    }

}
