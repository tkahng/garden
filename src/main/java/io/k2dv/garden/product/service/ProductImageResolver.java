package io.k2dv.garden.product.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductImage;
import io.k2dv.garden.product.repository.ProductImageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class ProductImageResolver {

    private final ProductImageRepository imageRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    public Map<UUID, String> resolveByProductId(Collection<Product> products) {
        Set<UUID> featuredImageIds = products.stream()
            .map(Product::getFeaturedImageId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (featuredImageIds.isEmpty()) return Map.of();

        Map<UUID, ProductImage> imagesById = imageRepo.findAllById(featuredImageIds).stream()
            .collect(Collectors.toMap(ProductImage::getId, img -> img));
        Set<UUID> blobIds = imagesById.values().stream()
            .map(ProductImage::getBlobId).collect(Collectors.toSet());
        Map<UUID, String> blobUrls = blobRepo.findAllById(blobIds).stream()
            .collect(Collectors.toMap(b -> b.getId(), b -> storageService.resolveUrl(b.getKey())));

        return products.stream()
            .filter(p -> p.getFeaturedImageId() != null)
            .filter(p -> imagesById.containsKey(p.getFeaturedImageId()))
            .filter(p -> blobUrls.containsKey(imagesById.get(p.getFeaturedImageId()).getBlobId()))
            .collect(Collectors.toMap(
                Product::getId,
                p -> blobUrls.get(imagesById.get(p.getFeaturedImageId()).getBlobId())));
    }
}
