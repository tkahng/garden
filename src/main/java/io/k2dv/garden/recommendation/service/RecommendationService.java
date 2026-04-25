package io.k2dv.garden.recommendation.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.product.dto.ProductSummaryResponse;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductImage;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductImageRepository;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class RecommendationService {

    private final ProductRepository productRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductImageRepository imageRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public List<ProductSummaryResponse> findRelated(String handle, int limit) {
        Product source = productRepo.findByHandle(handle)
            .filter(p -> p.getDeletedAt() == null)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        int clampedLimit = Math.min(limit, 12);
        List<Product> related = productRepo.findRelatedByTagOverlap(source.getId(), clampedLimit);
        return toSummaryResponses(related);
    }

    private List<ProductSummaryResponse> toSummaryResponses(List<Product> products) {
        if (products.isEmpty()) return List.of();

        Set<UUID> productIds = products.stream().map(Product::getId).collect(Collectors.toSet());
        Map<UUID, List<ProductVariant>> variantsByProduct = variantRepo
            .findByProductIdInAndDeletedAtIsNull(productIds).stream()
            .collect(Collectors.groupingBy(ProductVariant::getProductId));

        Set<UUID> featuredImageIds = products.stream()
            .map(Product::getFeaturedImageId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, String> resolvedImageUrls = Map.of();
        if (!featuredImageIds.isEmpty()) {
            Map<UUID, ProductImage> imagesById = imageRepo.findAllById(featuredImageIds).stream()
                .collect(Collectors.toMap(ProductImage::getId, img -> img));
            Set<UUID> blobIds = imagesById.values().stream()
                .map(ProductImage::getBlobId).collect(Collectors.toSet());
            Map<UUID, String> blobUrls = blobRepo.findAllById(blobIds).stream()
                .collect(Collectors.toMap(b -> b.getId(), b -> storageService.resolveUrl(b.getKey())));
            resolvedImageUrls = imagesById.entrySet().stream()
                .filter(e -> blobUrls.containsKey(e.getValue().getBlobId()))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> blobUrls.get(e.getValue().getBlobId())));
        }
        final Map<UUID, String> imageUrls = resolvedImageUrls;

        return products.stream().map(p -> {
            List<ProductVariant> variants = variantsByProduct.getOrDefault(p.getId(), List.of());
            BigDecimal priceMin = variants.stream().map(ProductVariant::getPrice)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            BigDecimal priceMax = variants.stream().map(ProductVariant::getPrice)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            BigDecimal compareAtMin = variants.stream().map(ProductVariant::getCompareAtPrice)
                .filter(Objects::nonNull).min(Comparator.naturalOrder()).orElse(null);
            BigDecimal compareAtMax = variants.stream().map(ProductVariant::getCompareAtPrice)
                .filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
            String imageUrl = p.getFeaturedImageId() != null ? imageUrls.get(p.getFeaturedImageId()) : null;
            return new ProductSummaryResponse(p.getId(), p.getTitle(), p.getHandle(), p.getVendor(),
                imageUrl, priceMin, priceMax, compareAtMin, compareAtMax);
        }).toList();
    }
}
