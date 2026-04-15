package io.k2dv.garden.blob.service;

import io.k2dv.garden.blob.dto.BlobFilter;
import io.k2dv.garden.blob.dto.BlobUsageResponse;
import io.k2dv.garden.blob.dto.UpdateBlobRequest;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BlobServiceIT extends AbstractIntegrationTest {

  @SuppressWarnings("deprecation")
  static final MinIOContainer minio = new MinIOContainer("minio/minio:RELEASE.2025-04-22T22-12-26Z");

  static {
    minio.start();
  }

  @DynamicPropertySource
  static void minioProperties(DynamicPropertyRegistry registry) {
    registry.add("storage.endpoint", minio::getS3URL);
    registry.add("storage.access-key", minio::getUserName);
    registry.add("storage.secret-key", minio::getPassword);
    registry.add("storage.bucket", () -> "test-bucket");
    registry.add("storage.base-url", () -> minio.getS3URL() + "/test-bucket");
  }

  @Autowired
  BlobService blobService;
  @Autowired
  BlobObjectRepository blobRepo;
  @Autowired
  StorageService storageService;
  @Autowired
  S3Client s3Client;

  String uploadedKey;

  @BeforeEach
  void ensureBucket() {
    try {
      s3Client.createBucket(b -> b.bucket("test-bucket"));
    } catch (BucketAlreadyOwnedByYouException ignored) {
    }
  }

  @AfterEach
  void cleanupMinio() {
    if (uploadedKey != null) {
      try {
        storageService.delete(uploadedKey);
      } catch (Exception ignored) {
      }
      uploadedKey = null;
    }
  }

  @Test
  void upload_persistsBlobObjectAndStoresInMinio() {
    var file = new MockMultipartFile("file", "hello.txt", "text/plain", "hello".getBytes());

    var resp = blobService.upload(file);
    uploadedKey = resp.key();

    assertThat(resp.id()).isNotNull();
    assertThat(resp.filename()).isEqualTo("hello.txt");
    assertThat(resp.contentType()).isEqualTo("text/plain");
    assertThat(resp.size()).isEqualTo(5L);
    assertThat(resp.url()).contains("hello.txt");
    assertThat(resp.createdAt()).isNotNull();
    assertThat(blobRepo.findById(resp.id())).isPresent();
  }

  @Test
  void delete_removesBlobObjectFromDbAndMinio() {
    var file = new MockMultipartFile("file", "bye.txt", "text/plain", "bye".getBytes());
    var resp = blobService.upload(file);
    uploadedKey = resp.key();

    blobService.delete(resp.id());
    uploadedKey = null;

    assertThat(blobRepo.findById(resp.id())).isEmpty();
  }

  @Test
  void updateMetadata_persistsAltAndTitle() {
    var file = new MockMultipartFile("file", "img.txt", "text/plain", "data".getBytes());
    var resp = blobService.upload(file);
    uploadedKey = resp.key();

    var updated = blobService.updateMetadata(resp.id(), new UpdateBlobRequest("alt text", "My Title"));

    assertThat(updated.alt()).isEqualTo("alt text");
    assertThat(updated.title()).isEqualTo("My Title");
  }

  @Test
  void bulkDelete_removesAllFromDbAndMinio() {
    var f1 = new MockMultipartFile("file", "a.txt", "text/plain", "a".getBytes());
    var f2 = new MockMultipartFile("file", "b.txt", "text/plain", "b".getBytes());
    var r1 = blobService.upload(f1);
    var r2 = blobService.upload(f2);

    blobService.bulkDelete(List.of(r1.id(), r2.id()));

    assertThat(blobRepo.findById(r1.id())).isEmpty();
    assertThat(blobRepo.findById(r2.id())).isEmpty();
  }

  @Test
  void list_sortByFilenameAsc_returnsOrdered() {
    var fa = new MockMultipartFile("file", "alpha.txt", "text/plain", "a".getBytes());
    var fb = new MockMultipartFile("file", "beta.txt", "text/plain", "b".getBytes());
    var ra = blobService.upload(fa);
    var rb = blobService.upload(fb);

    var result = blobService.list(
        new BlobFilter(null, null, "filename", "asc"),
        PageRequest.of(0, 100));

    var filenames = result.getContent().stream().map(r -> r.filename()).toList();
    int ia = filenames.indexOf("alpha.txt");
    int ib = filenames.indexOf("beta.txt");
    assertThat(ia).isLessThan(ib);

    blobService.bulkDelete(List.of(ra.id(), rb.id()));
  }

  @Test
  void getUsages_noUsages_returnsEmpty() {
    var file = new MockMultipartFile("file", "unused.txt", "text/plain", "u".getBytes());
    var resp = blobService.upload(file);
    uploadedKey = resp.key();

    List<BlobUsageResponse> usages = blobService.getUsages(resp.id());

    assertThat(usages).isEmpty();
  }
}
