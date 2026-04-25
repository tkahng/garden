package io.k2dv.garden.wishlist.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductImage;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductImageRepository;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.wishlist.dto.WishlistItemResponse;
import io.k2dv.garden.wishlist.dto.WishlistResponse;
import io.k2dv.garden.wishlist.model.Wishlist;
import io.k2dv.garden.wishlist.model.WishlistItem;
import io.k2dv.garden.wishlist.repository.WishlistItemRepository;
import io.k2dv.garden.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class WishlistService {

    private final WishlistRepository wishlistRepo;
    private final WishlistItemRepository wishlistItemRepo;
    private final ProductRepository productRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductImageRepository imageRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    @Transactional(readOnly = true)
    public WishlistResponse getWishlist(UUID userId) {
        Optional<Wishlist> wishlist = wishlistRepo.findByUserId(userId);
        if (wishlist.isEmpty()) {
            return new WishlistResponse(null, List.of());
        }
        List<WishlistItem> items = wishlistItemRepo.findByWishlistId(wishlist.get().getId());
        return new WishlistResponse(wishlist.get().getId(), toItemResponses(items));
    }

    @Transactional
    public WishlistResponse addItem(UUID userId, UUID productId) {
        productRepo.findByIdAndDeletedAtIsNull(productId)
            .filter(p -> p.getStatus() == ProductStatus.ACTIVE)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        Wishlist wishlist = wishlistRepo.findByUserId(userId).orElseGet(() -> {
            Wishlist w = new Wishlist();
            w.setUserId(userId);
            return wishlistRepo.save(w);
        });

        if (wishlistItemRepo.existsByWishlistIdAndProductId(wishlist.getId(), productId)) {
            throw new ConflictException("ALREADY_IN_WISHLIST", "Product is already in your wishlist");
        }

        WishlistItem item = new WishlistItem();
        item.setWishlistId(wishlist.getId());
        item.setProductId(productId);
        wishlistItemRepo.save(item);

        List<WishlistItem> items = wishlistItemRepo.findByWishlistId(wishlist.getId());
        return new WishlistResponse(wishlist.getId(), toItemResponses(items));
    }

    @Transactional
    public WishlistResponse removeItem(UUID userId, UUID productId) {
        Wishlist wishlist = wishlistRepo.findByUserId(userId)
            .orElseThrow(() -> new NotFoundException("WISHLIST_NOT_FOUND", "Wishlist not found"));
        wishlistItemRepo.deleteByWishlistIdAndProductId(wishlist.getId(), productId);
        List<WishlistItem> items = wishlistItemRepo.findByWishlistId(wishlist.getId());
        return new WishlistResponse(wishlist.getId(), toItemResponses(items));
    }

    private List<WishlistItemResponse> toItemResponses(List<WishlistItem> items) {
        if (items.isEmpty()) return List.of();

        Set<UUID> productIds = items.stream().map(WishlistItem::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> productsById = productRepo.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        // Batch load featured image URLs
        Set<UUID> featuredImageIds = productsById.values().stream()
            .map(Product::getFeaturedImageId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        Map<UUID, String> featuredImageUrls = Map.of();
        if (!featuredImageIds.isEmpty()) {
            Map<UUID, ProductImage> imagesById = imageRepo.findAllById(featuredImageIds).stream()
                .collect(Collectors.toMap(ProductImage::getId, img -> img));
            Set<UUID> blobIds = imagesById.values().stream()
                .map(ProductImage::getBlobId).collect(Collectors.toSet());
            Map<UUID, String> blobUrls = blobRepo.findAllById(blobIds).stream()
                .collect(Collectors.toMap(b -> b.getId(), b -> storageService.resolveUrl(b.getKey())));
            featuredImageUrls = imagesById.entrySet().stream()
                .filter(e -> blobUrls.containsKey(e.getValue().getBlobId()))
                .collect(Collectors.toMap(Map.Entry::getKey, e -> blobUrls.get(e.getValue().getBlobId())));
        }
        final Map<UUID, String> resolvedImageUrls = featuredImageUrls;

        // Batch load variant prices
        Map<UUID, List<ProductVariant>> variantsByProduct = variantRepo
            .findByProductIdInAndDeletedAtIsNull(productIds).stream()
            .collect(Collectors.groupingBy(ProductVariant::getProductId));

        return items.stream().map(item -> {
            Product p = productsById.get(item.getProductId());
            if (p == null) return null;
            String imageUrl = p.getFeaturedImageId() != null
                ? resolvedImageUrls.get(p.getFeaturedImageId()) : null;
            BigDecimal priceMin = variantsByProduct.getOrDefault(p.getId(), List.of()).stream()
                .map(ProductVariant::getPrice)
                .filter(Objects::nonNull)
                .min(Comparator.naturalOrder())
                .orElse(null);
            return new WishlistItemResponse(item.getId(), p.getId(), p.getTitle(), p.getHandle(), imageUrl, priceMin);
        }).filter(Objects::nonNull).toList();
    }
}
