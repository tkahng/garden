package io.k2dv.garden.product.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.product.dto.CreateImageRequest;
import io.k2dv.garden.product.dto.ImagePositionItem;
import io.k2dv.garden.product.dto.ProductImageResponse;
import io.k2dv.garden.product.model.ProductImage;
import io.k2dv.garden.product.repository.ProductImageRepository;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Manages the gallery of images attached to a product, backed by pre-uploaded blob objects.
 * Automatically maintains the product's featured image pointer, promoting the next image
 * in position order when the current featured image is removed.
 */
@Service
@RequiredArgsConstructor
public class ProductImageService {

    private final ProductImageRepository imageRepo;
    private final ProductRepository productRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;

    /**
     * Attaches a previously uploaded blob as a product image and appends it at the next
     * position. If this is the first image on the product, it is automatically set as the
     * featured image.
     */
    @Transactional
    public ProductImageResponse addImage(UUID productId, CreateImageRequest req) {
        var product = productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        int nextPosition = imageRepo.countByProductId(productId) + 1;

        ProductImage img = new ProductImage();
        img.setProductId(productId);
        img.setBlobId(req.blobId());
        img.setAltText(req.altText());
        img.setPosition(nextPosition);
        img = imageRepo.save(img);

        // Auto-set featuredImageId if this is the first image
        if (product.getFeaturedImageId() == null) {
            product.setFeaturedImageId(img.getId());
            productRepo.save(product);
        }

        return toResponse(img);
    }

    /**
     * Removes an image from the product gallery. If the deleted image was the featured image,
     * the next image by position is automatically promoted to featured.
     */
    @Transactional
    public void deleteImage(UUID productId, UUID imageId) {
        var product = productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        var img = imageRepo.findById(imageId)
            .filter(i -> i.getProductId().equals(productId))
            .orElseThrow(() -> new NotFoundException("IMAGE_NOT_FOUND", "Image not found"));

        boolean wasFeatured = imageId.equals(product.getFeaturedImageId());
        imageRepo.delete(img);

        if (wasFeatured) {
            // Promote next image by lowest position
            List<ProductImage> remaining = imageRepo.findByProductIdOrderByPositionAsc(productId);
            product.setFeaturedImageId(remaining.isEmpty() ? null : remaining.get(0).getId());
            productRepo.save(product);
        }
    }

    /**
     * Updates the display order of product images in a single transaction by applying the
     * provided position values. Images not included in the list are left unchanged.
     */
    @Transactional
    public void reorderImages(UUID productId, List<ImagePositionItem> items) {
        productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        for (ImagePositionItem item : items) {
            imageRepo.findById(item.id())
                .filter(i -> i.getProductId().equals(productId))
                .ifPresent(i -> i.setPosition(item.position()));
        }
    }

    private ProductImageResponse toResponse(ProductImage img) {
        String url = blobRepo.findById(img.getBlobId())
            .map(b -> storageService.resolveUrl(b.getKey()))
            .orElse("");
        return new ProductImageResponse(img.getId(), url, img.getAltText(), img.getPosition());
    }
}
