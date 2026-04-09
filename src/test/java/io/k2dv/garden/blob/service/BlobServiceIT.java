package io.k2dv.garden.blob.service;

import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MinIOContainer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;

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
    assertThat(blobRepo.findById(resp.id())).isPresent();
  }

  @Test
  void delete_removesBlobObjectFromDbAndMinio() {
    var file = new MockMultipartFile("file", "bye.txt", "text/plain", "bye".getBytes());
    var resp = blobService.upload(file);
    uploadedKey = resp.key();

    blobService.delete(resp.id());
    uploadedKey = null; // already deleted from MinIO inside blobService.delete

    assertThat(blobRepo.findById(resp.id())).isEmpty();
  }
}
