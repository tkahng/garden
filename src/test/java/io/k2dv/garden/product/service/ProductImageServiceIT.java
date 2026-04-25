package io.k2dv.garden.product.service;

import io.k2dv.garden.blob.model.BlobObject;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.product.dto.CreateImageRequest;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ProductImageServiceIT extends AbstractIntegrationTest {

    @Autowired ProductService productService;
    @Autowired ProductImageService imageService;
    @Autowired ProductRepository productRepo;
    @Autowired BlobObjectRepository blobRepo;

    UUID productId;
    UUID blobId;

    @BeforeEach
    void setup() {
        // Create a dummy BlobObject to satisfy FK
        BlobObject blob = new BlobObject();
        blob.setKey("uploads/test.jpg");
        blob.setFilename("test.jpg");
        blob.setContentType("image/jpeg");
        blob.setSize(100L);
        blobId = blobRepo.save(blob).getId();

        productId = productService.create(
            new CreateProductRequest("Img Product", null, null, null, null, List.of(), null, null)
        ).id();
    }

    @Test
    void addFirstImage_setsFeaturedImageId() {
        var img = imageService.addImage(productId, new CreateImageRequest(blobId, "alt"));
        var product = productRepo.findByIdAndDeletedAtIsNull(productId).orElseThrow();
        assertThat(product.getFeaturedImageId()).isEqualTo(img.id());
    }

    @Test
    void addSecondImage_doesNotChangeFeaturedImageId() {
        var first = imageService.addImage(productId, new CreateImageRequest(blobId, "first"));

        BlobObject blob2 = new BlobObject();
        blob2.setKey("uploads/test2.jpg");
        blob2.setFilename("test2.jpg");
        blob2.setContentType("image/jpeg");
        blob2.setSize(100L);
        UUID blobId2 = blobRepo.save(blob2).getId();

        imageService.addImage(productId, new CreateImageRequest(blobId2, "second"));
        var product = productRepo.findByIdAndDeletedAtIsNull(productId).orElseThrow();
        assertThat(product.getFeaturedImageId()).isEqualTo(first.id());
    }

    @Test
    void deleteFeaturedImage_promoteNextByPosition() {
        var first = imageService.addImage(productId, new CreateImageRequest(blobId, "first"));

        BlobObject blob2 = new BlobObject();
        blob2.setKey("uploads/second.jpg");
        blob2.setFilename("second.jpg");
        blob2.setContentType("image/jpeg");
        blob2.setSize(100L);
        UUID blobId2 = blobRepo.save(blob2).getId();
        var second = imageService.addImage(productId, new CreateImageRequest(blobId2, "second"));

        imageService.deleteImage(productId, first.id());
        var product = productRepo.findByIdAndDeletedAtIsNull(productId).orElseThrow();
        assertThat(product.getFeaturedImageId()).isEqualTo(second.id());
    }

    @Test
    void deleteLastImage_setsFeaturedImageIdNull() {
        imageService.addImage(productId, new CreateImageRequest(blobId, "only"));
        var img = productRepo.findByIdAndDeletedAtIsNull(productId).orElseThrow().getFeaturedImageId();
        assertThat(img).isNotNull();

        imageService.deleteImage(productId, img);
        var product = productRepo.findByIdAndDeletedAtIsNull(productId).orElseThrow();
        assertThat(product.getFeaturedImageId()).isNull();
    }
}
