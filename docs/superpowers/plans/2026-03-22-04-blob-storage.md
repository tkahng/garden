# Blob Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add S3-compatible blob storage (MinIO locally, Cloudflare R2 in prod) with a `BlobObject` entity, upload/delete API, and integration tests.

**Architecture:** A `blob` package owns all storage concerns — `StorageService` interface, `S3StorageService` (AWS SDK v2), `BlobService` (orchestrator), and `BlobController`. No other domain calls storage directly. One implementation serves all environments via config only.

**Tech Stack:** Spring Boot 4.0.4, AWS SDK v2 (`software.amazon.awssdk:s3`), Testcontainers MinIO, JPA/Flyway, MockMvc

---

## File Map

**New files:**
- `src/main/resources/db/migration/V8__create_blob_objects.sql`
- `src/main/java/io/k2dv/garden/shared/exception/ValidationException.java`
- `src/main/java/io/k2dv/garden/blob/config/StorageProperties.java`
- `src/main/java/io/k2dv/garden/blob/config/StorageConfig.java`
- `src/main/java/io/k2dv/garden/blob/model/BlobObject.java`
- `src/main/java/io/k2dv/garden/blob/repository/BlobObjectRepository.java`
- `src/main/java/io/k2dv/garden/blob/service/StorageService.java`
- `src/main/java/io/k2dv/garden/blob/service/S3StorageService.java`
- `src/main/java/io/k2dv/garden/blob/dto/BlobResponse.java`
- `src/main/java/io/k2dv/garden/blob/service/BlobService.java`
- `src/main/java/io/k2dv/garden/blob/controller/BlobController.java`
- `src/test/java/io/k2dv/garden/blob/service/BlobServiceIT.java`
- `src/test/java/io/k2dv/garden/blob/controller/BlobControllerTest.java`

**Modified files:**
- `pom.xml` — add AWS SDK v2 BOM + s3 dependency + testcontainers:minio
- `src/test/resources/application-test.properties` — add dummy `storage.*` properties
- `src/main/java/io/k2dv/garden/shared/exception/GlobalExceptionHandler.java` — add `MaxUploadSizeExceededException` handler

---

### Task 1: pom.xml + Flyway migration + test properties

**Files:**
- Modify: `pom.xml`
- Create: `src/main/resources/db/migration/V8__create_blob_objects.sql`
- Modify: `src/test/resources/application-test.properties`

- [ ] **Step 1: Add AWS SDK v2 BOM to `pom.xml` dependencyManagement**

  In `pom.xml`, add inside `<dependencyManagement><dependencies>` (create the `<dependencyManagement>` block if it doesn't exist — it doesn't currently):

  ```xml
  <dependencyManagement>
    <dependencies>
      <dependency>
        <groupId>software.amazon.awssdk</groupId>
        <artifactId>bom</artifactId>
        <version>2.29.52</version>
        <type>pom</type>
        <scope>import</scope>
      </dependency>
    </dependencies>
  </dependencyManagement>
  ```

  Place this block before `<dependencies>` in `pom.xml`.

- [ ] **Step 2: Add runtime and test dependencies to `pom.xml`**

  Inside `<dependencies>`, add:

  ```xml
  <!-- AWS SDK v2 S3 client -->
  <dependency>
    <groupId>software.amazon.awssdk</groupId>
    <artifactId>s3</artifactId>
  </dependency>

  <!-- Testcontainers MinIO -->
  <dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>minio</artifactId>
    <scope>test</scope>
  </dependency>
  ```

- [ ] **Step 3: Create `V8__create_blob_objects.sql`**

  Create `src/main/resources/db/migration/V8__create_blob_objects.sql`:

  ```sql
  CREATE TABLE blob_objects (
      id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
      key          TEXT        NOT NULL UNIQUE,
      filename     TEXT        NOT NULL,
      content_type TEXT        NOT NULL,
      size         BIGINT      NOT NULL,
      created_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
      updated_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
  );

  CREATE TRIGGER set_updated_at
      BEFORE UPDATE ON blob_objects
      FOR EACH ROW EXECUTE FUNCTION set_updated_at();

  INSERT INTO permissions (id, name, resource, action, created_at, updated_at) VALUES
      ('00000000-0000-7000-8000-000000000025', 'blob:upload', 'blob', 'upload', clock_timestamp(), clock_timestamp()),
      ('00000000-0000-7000-8000-000000000026', 'blob:delete', 'blob', 'delete', clock_timestamp(), clock_timestamp())
  ON CONFLICT (name) DO NOTHING;

  INSERT INTO role_permissions (role_id, permission_id)
  SELECT r.id, p.id
  FROM roles r, permissions p
  WHERE r.name IN ('OWNER', 'MANAGER')
    AND p.name IN ('blob:upload', 'blob:delete')
  ON CONFLICT DO NOTHING;
  ```

- [ ] **Step 4: Add dummy storage properties to `application-test.properties`**

  Append to `src/test/resources/application-test.properties`:

  ```properties
  # Dummy storage config — satisfies @Validated on StorageProperties in non-blob ITs
  # BlobServiceIT overrides these via @DynamicPropertySource
  storage.endpoint=http://localhost:9000
  storage.bucket=test
  storage.access-key=test
  storage.secret-key=test
  storage.base-url=http://localhost:9000/test
  ```

- [ ] **Step 5: Verify existing tests still pass**

  Run: `./mvnw test`

  Expected: `Tests run: 52, Failures: 0, Errors: 0, Skipped: 0` — migration is clean, test properties have no compile issues yet (StorageProperties class doesn't exist yet, so `@Validated` isn't enforced — that comes in Task 2).

- [ ] **Step 6: Commit**

  ```bash
  git add pom.xml \
    src/main/resources/db/migration/V8__create_blob_objects.sql \
    src/test/resources/application-test.properties
  git commit -m "feat(blob): add pom deps, V8 migration, and test storage properties"
  ```

---

### Task 2: ValidationException + StorageProperties + StorageConfig

**Files:**
- Create: `src/main/java/io/k2dv/garden/shared/exception/ValidationException.java`
- Create: `src/main/java/io/k2dv/garden/blob/config/StorageProperties.java`
- Create: `src/main/java/io/k2dv/garden/blob/config/StorageConfig.java`

**Context:** `DomainException` is in `io.k2dv.garden.shared.exception`. All subclasses take `(code, message, HttpStatus)`. `GlobalExceptionHandler` handles `DomainException` and maps it to its declared `HttpStatus` — so `ValidationException` with `BAD_REQUEST` automatically returns 400 with no extra handler needed. The `StorageConfig` pattern follows `AppPropertiesConfig`: a `@Configuration` class that carries `@EnableConfigurationProperties` to register the properties bean.

- [ ] **Step 1: Create `ValidationException.java`**

  Create `src/main/java/io/k2dv/garden/shared/exception/ValidationException.java`:

  ```java
  package io.k2dv.garden.shared.exception;

  import org.springframework.http.HttpStatus;

  public class ValidationException extends DomainException {

      public ValidationException(String code, String message) {
          super(code, message, HttpStatus.BAD_REQUEST);
      }
  }
  ```

- [ ] **Step 2: Create `StorageProperties.java`**

  Create `src/main/java/io/k2dv/garden/blob/config/StorageProperties.java`:

  ```java
  package io.k2dv.garden.blob.config;

  import jakarta.validation.constraints.NotBlank;
  import lombok.Getter;
  import lombok.Setter;
  import org.springframework.boot.context.properties.ConfigurationProperties;
  import org.springframework.validation.annotation.Validated;

  @ConfigurationProperties(prefix = "storage")
  @Validated
  @Getter
  @Setter
  public class StorageProperties {

      @NotBlank
      private String endpoint;

      @NotBlank
      private String bucket;

      @NotBlank
      private String accessKey;

      @NotBlank
      private String secretKey;

      @NotBlank
      private String baseUrl;

      private long maxUploadSize = 10_485_760L; // 10 MB default
  }
  ```

- [ ] **Step 3: Create `StorageConfig.java`**

  Create `src/main/java/io/k2dv/garden/blob/config/StorageConfig.java`:

  ```java
  package io.k2dv.garden.blob.config;

  import org.springframework.boot.context.properties.EnableConfigurationProperties;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
  import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
  import software.amazon.awssdk.regions.Region;
  import software.amazon.awssdk.services.s3.S3Client;
  import software.amazon.awssdk.services.s3.S3Configuration;

  import java.net.URI;

  @Configuration
  @EnableConfigurationProperties(StorageProperties.class)
  public class StorageConfig {

      @Bean
      S3Client s3Client(StorageProperties props) {
          return S3Client.builder()
              .endpointOverride(URI.create(props.getEndpoint()))
              .credentialsProvider(StaticCredentialsProvider.create(
                  AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey())))
              .region(Region.US_EAST_1) // required by SDK; ignored by MinIO and R2
              .serviceConfiguration(S3Configuration.builder()
                  .pathStyleAccessEnabled(true) // required for MinIO path-style addressing
                  .build())
              .build();
      }
  }
  ```

- [ ] **Step 4: Run existing tests to verify context still loads**

  Run: `./mvnw test`

  Expected: `Tests run: 52, Failures: 0, Errors: 0, Skipped: 0`. The dummy properties in `application-test.properties` satisfy `@NotBlank` so `@Validated` does not fail startup. The `S3Client` bean is constructed but never invoked in non-blob tests.

- [ ] **Step 5: Commit**

  ```bash
  git add src/main/java/io/k2dv/garden/shared/exception/ValidationException.java \
    src/main/java/io/k2dv/garden/blob/config/StorageProperties.java \
    src/main/java/io/k2dv/garden/blob/config/StorageConfig.java
  git commit -m "feat(blob): add ValidationException, StorageProperties, StorageConfig"
  ```

---

### Task 3: BlobObject entity + BlobObjectRepository

**Files:**
- Create: `src/main/java/io/k2dv/garden/blob/model/BlobObject.java`
- Create: `src/main/java/io/k2dv/garden/blob/repository/BlobObjectRepository.java`

**Context:** All entities extend `BaseEntity` which provides `id` (UUIDv7 via `@UuidGenerator`), `createdAt`, and `updatedAt` (both `@Generated` from the DB). Look at `User.java` for the established pattern. The `content_type` DB column maps to `contentType` Java field via `@Column(name = "content_type")`.

- [ ] **Step 1: Create `BlobObject.java`**

  Create `src/main/java/io/k2dv/garden/blob/model/BlobObject.java`:

  ```java
  package io.k2dv.garden.blob.model;

  import io.k2dv.garden.shared.model.BaseEntity;
  import jakarta.persistence.Column;
  import jakarta.persistence.Entity;
  import jakarta.persistence.Table;
  import lombok.Getter;
  import lombok.Setter;

  @Entity
  @Table(name = "blob_objects")
  @Getter
  @Setter
  public class BlobObject extends BaseEntity {

      @Column(nullable = false, unique = true)
      private String key;

      @Column(nullable = false)
      private String filename;

      @Column(name = "content_type", nullable = false)
      private String contentType;

      @Column(nullable = false)
      private long size;
  }
  ```

- [ ] **Step 2: Create `BlobObjectRepository.java`**

  Create `src/main/java/io/k2dv/garden/blob/repository/BlobObjectRepository.java`:

  ```java
  package io.k2dv.garden.blob.repository;

  import io.k2dv.garden.blob.model.BlobObject;
  import org.springframework.data.jpa.repository.JpaRepository;

  import java.util.UUID;

  public interface BlobObjectRepository extends JpaRepository<BlobObject, UUID> {
  }
  ```

- [ ] **Step 3: Run tests to verify Hibernate schema validation passes**

  Run: `./mvnw test`

  Expected: `Tests run: 52, Failures: 0, Errors: 0, Skipped: 0`. Hibernate validates `blob_objects` table against `BlobObject` entity — should match since V8 migration created it.

- [ ] **Step 4: Commit**

  ```bash
  git add src/main/java/io/k2dv/garden/blob/model/BlobObject.java \
    src/main/java/io/k2dv/garden/blob/repository/BlobObjectRepository.java
  git commit -m "feat(blob): add BlobObject entity and BlobObjectRepository"
  ```

---

### Task 4: StorageService + S3StorageService + BlobResponse + BlobService + BlobServiceIT (TDD)

**Files:**
- Create: `src/test/java/io/k2dv/garden/blob/service/BlobServiceIT.java` ← write first
- Create: `src/main/java/io/k2dv/garden/blob/service/StorageService.java`
- Create: `src/main/java/io/k2dv/garden/blob/service/S3StorageService.java`
- Create: `src/main/java/io/k2dv/garden/blob/dto/BlobResponse.java`
- Create: `src/main/java/io/k2dv/garden/blob/service/BlobService.java`

**Context:**
- `BlobServiceIT` extends `AbstractIntegrationTest` (PostgreSQL Testcontainer, `@Transactional @Rollback`). It also starts a `MinIOContainer` in a `static {}` block and registers it via `@DynamicPropertySource` — same pattern as `AbstractIntegrationTest`.
- Because `BlobServiceIT` registers extra `storage.*` properties, Spring creates a separate cached context for it. This is expected and normal.
- `@Transactional @Rollback` means DB state is rolled back after each test. MinIO state is NOT rolled back — clean up in `@AfterEach` via `storageService.delete(key)`.
- The MinIO container starts without any buckets. Create the `test-bucket` in `@BeforeEach` using the injected `S3Client` (ignore `BucketAlreadyOwnedByYouException` on repeat calls).
- `BlobService.upload()` and `BlobService.delete()` are `@Transactional(REQUIRED)` — they participate in the test's transaction.
- `S3StorageService.store()` uses `RequestBody.fromInputStream(data, size)` — the `contentLength` must be provided so the SDK doesn't attempt chunked encoding (MinIO doesn't support chunked uploads by default).

- [ ] **Step 1: Write the failing `BlobServiceIT.java`**

  Create `src/test/java/io/k2dv/garden/blob/service/BlobServiceIT.java`:

  ```java
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
      static final MinIOContainer minio = new MinIOContainer("minio/minio:latest");

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

      @Autowired BlobService blobService;
      @Autowired BlobObjectRepository blobRepo;
      @Autowired StorageService storageService;
      @Autowired S3Client s3Client;

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
  ```

- [ ] **Step 2: Run the test to verify it fails (compilation error — classes don't exist yet)**

  Run: `./mvnw test -pl . -Dtest=BlobServiceIT -q`

  Expected: COMPILE ERROR — `BlobService`, `StorageService` not found.

- [ ] **Step 3: Create `StorageService.java`**

  Create `src/main/java/io/k2dv/garden/blob/service/StorageService.java`:

  ```java
  package io.k2dv.garden.blob.service;

  import java.io.InputStream;

  public interface StorageService {

      /**
       * Store an object at the given key. Key is generated by the caller.
       * contentLength must be provided — MinIO does not support chunked uploads.
       */
      void store(String key, String contentType, InputStream data, long contentLength);

      /** Delete the object at the given key. Idempotent — no error if key absent. */
      void delete(String key);

      /** Compute the public URL for a key: baseUrl + "/" + key. */
      String resolveUrl(String key);
  }
  ```

- [ ] **Step 4: Create `S3StorageService.java`**

  Create `src/main/java/io/k2dv/garden/blob/service/S3StorageService.java`:

  ```java
  package io.k2dv.garden.blob.service;

  import io.k2dv.garden.blob.config.StorageProperties;
  import lombok.RequiredArgsConstructor;
  import org.springframework.stereotype.Service;
  import software.amazon.awssdk.core.sync.RequestBody;
  import software.amazon.awssdk.services.s3.S3Client;
  import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
  import software.amazon.awssdk.services.s3.model.PutObjectRequest;

  import java.io.InputStream;

  @Service
  @RequiredArgsConstructor
  public class S3StorageService implements StorageService {

      private final S3Client s3Client;
      private final StorageProperties properties;

      @Override
      public void store(String key, String contentType, InputStream data, long contentLength) {
          PutObjectRequest request = PutObjectRequest.builder()
              .bucket(properties.getBucket())
              .key(key)
              .contentType(contentType)
              .contentLength(contentLength)
              .build();
          s3Client.putObject(request, RequestBody.fromInputStream(data, contentLength));
      }

      @Override
      public void delete(String key) {
          DeleteObjectRequest request = DeleteObjectRequest.builder()
              .bucket(properties.getBucket())
              .key(key)
              .build();
          s3Client.deleteObject(request);
      }

      @Override
      public String resolveUrl(String key) {
          return properties.getBaseUrl() + "/" + key;
      }
  }
  ```

- [ ] **Step 5: Create `BlobResponse.java`**

  Create `src/main/java/io/k2dv/garden/blob/dto/BlobResponse.java`:

  ```java
  package io.k2dv.garden.blob.dto;

  import io.k2dv.garden.blob.model.BlobObject;

  import java.util.UUID;

  public record BlobResponse(
      UUID id,
      String key,
      String filename,
      String contentType,
      long size,
      String url
  ) {
      public static BlobResponse from(BlobObject blob, String url) {
          return new BlobResponse(
              blob.getId(),
              blob.getKey(),
              blob.getFilename(),
              blob.getContentType(),
              blob.getSize(),
              url);
      }
  }
  ```

- [ ] **Step 6: Create `BlobService.java`**

  Create `src/main/java/io/k2dv/garden/blob/service/BlobService.java`:

  ```java
  package io.k2dv.garden.blob.service;

  import io.k2dv.garden.blob.config.StorageProperties;
  import io.k2dv.garden.blob.dto.BlobResponse;
  import io.k2dv.garden.blob.model.BlobObject;
  import io.k2dv.garden.blob.repository.BlobObjectRepository;
  import io.k2dv.garden.shared.exception.NotFoundException;
  import io.k2dv.garden.shared.exception.ValidationException;
  import lombok.RequiredArgsConstructor;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;
  import org.springframework.web.multipart.MultipartFile;

  import java.io.IOException;
  import java.util.UUID;

  @Service
  @RequiredArgsConstructor
  public class BlobService {

      private final BlobObjectRepository blobRepo;
      private final StorageService storageService;
      private final StorageProperties storageProperties;

      @Transactional
      public BlobResponse upload(MultipartFile file) {
          if (file.getSize() > storageProperties.getMaxUploadSize()) {
              throw new ValidationException("FILE_TOO_LARGE", "File exceeds maximum upload size");
          }
          String sanitized = sanitize(file.getOriginalFilename());
          String key = "uploads/" + UUID.randomUUID() + "-" + sanitized;
          try {
              storageService.store(key, contentType(file), file.getInputStream(), file.getSize());
          } catch (IOException e) {
              throw new RuntimeException("Failed to read upload stream", e);
          }
          BlobObject blob = new BlobObject();
          blob.setKey(key);
          blob.setFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
          blob.setContentType(contentType(file));
          blob.setSize(file.getSize());
          blob = blobRepo.save(blob);
          return BlobResponse.from(blob, storageService.resolveUrl(key));
      }

      @Transactional
      public void delete(UUID id) {
          BlobObject blob = blobRepo.findById(id)
              .orElseThrow(() -> new NotFoundException("BLOB_NOT_FOUND", "Blob not found"));
          storageService.delete(blob.getKey());
          blobRepo.delete(blob);
      }

      private String sanitize(String filename) {
          if (filename == null || filename.isBlank()) return "file";
          String name = filename.toLowerCase().replaceAll("[^a-z0-9.\\-]", "");
          return name.isBlank() ? "file" : name;
      }

      private String contentType(MultipartFile file) {
          return file.getContentType() != null ? file.getContentType() : "application/octet-stream";
      }
  }
  ```

- [ ] **Step 7: Run `BlobServiceIT` to verify it passes**

  Run: `./mvnw test -Dtest=BlobServiceIT`

  Expected: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 8: Run full test suite**

  Run: `./mvnw test`

  Expected: `Tests run: 54, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 9: Commit**

  ```bash
  git add src/main/java/io/k2dv/garden/blob/service/ \
    src/main/java/io/k2dv/garden/blob/dto/ \
    src/test/java/io/k2dv/garden/blob/service/BlobServiceIT.java
  git commit -m "feat(blob): add StorageService, S3StorageService, BlobService, BlobResponse, BlobServiceIT"
  ```

---

### Task 5: BlobController + GlobalExceptionHandler update + BlobControllerTest (TDD)

**Files:**
- Create: `src/test/java/io/k2dv/garden/blob/controller/BlobControllerTest.java` ← write first
- Create: `src/main/java/io/k2dv/garden/blob/controller/BlobController.java`
- Modify: `src/main/java/io/k2dv/garden/shared/exception/GlobalExceptionHandler.java`

**Context:**
- `@WebMvcTest` slice imports `TestSecurityConfig` (disables `@HasPermission`, permits all requests) and `GlobalExceptionHandler`.
- `BlobController` does NOT use `@CurrentUser` — no need to mock `CurrentUserArgumentResolver`.
- The controller test mocks `BlobService` and checks HTTP status + JSON shape.
- `GlobalExceptionHandler` uses `ErrorResponse` (not `ApiResponse`) for errors. The existing response format: `{ "error": "CODE", "message": "...", "status": 400 }`.
- `MaxUploadSizeExceededException` is in `org.springframework.web.multipart.MaxUploadSizeExceededException`.
- For the oversized file test: since `BlobService` is mocked, configure it to throw `ValidationException` to simulate the size check path.

- [ ] **Step 1: Write the failing `BlobControllerTest.java`**

  Create `src/test/java/io/k2dv/garden/blob/controller/BlobControllerTest.java`:

  ```java
  package io.k2dv.garden.blob.controller;

  import io.k2dv.garden.blob.dto.BlobResponse;
  import io.k2dv.garden.blob.service.BlobService;
  import io.k2dv.garden.config.TestSecurityConfig;
  import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
  import io.k2dv.garden.shared.exception.ValidationException;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
  import org.springframework.context.annotation.Import;
  import org.springframework.mock.web.MockMultipartFile;
  import org.springframework.test.context.bean.override.mockito.MockitoBean;
  import org.springframework.test.web.servlet.MockMvc;

  import java.util.UUID;

  import static org.mockito.ArgumentMatchers.any;
  import static org.mockito.Mockito.doNothing;
  import static org.mockito.Mockito.when;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
  import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
  import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

  @WebMvcTest(controllers = BlobController.class)
  @Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
  class BlobControllerTest {

      @Autowired
      MockMvc mvc;

      @MockitoBean
      BlobService blobService;

      @Test
      void upload_validFile_returns201() throws Exception {
          UUID id = UUID.randomUUID();
          var resp = new BlobResponse(id, "uploads/abc-test.jpg", "test.jpg", "image/jpeg", 4L,
              "http://localhost:9000/test/uploads/abc-test.jpg");
          when(blobService.upload(any())).thenReturn(resp);

          mvc.perform(multipart("/api/v1/admin/blobs")
                  .file(new MockMultipartFile("file", "test.jpg", "image/jpeg", "data".getBytes())))
              .andExpect(status().isCreated())
              .andExpect(jsonPath("$.data.filename").value("test.jpg"))
              .andExpect(jsonPath("$.data.key").value("uploads/abc-test.jpg"))
              .andExpect(jsonPath("$.data.url").exists());
      }

      @Test
      void upload_oversizedFile_returns400() throws Exception {
          when(blobService.upload(any()))
              .thenThrow(new ValidationException("FILE_TOO_LARGE", "File exceeds maximum upload size"));

          mvc.perform(multipart("/api/v1/admin/blobs")
                  .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", "data".getBytes())))
              .andExpect(status().isBadRequest())
              .andExpect(jsonPath("$.error").value("FILE_TOO_LARGE"));
      }

      @Test
      void delete_returns204() throws Exception {
          doNothing().when(blobService).delete(any());

          mvc.perform(delete("/api/v1/admin/blobs/{id}", UUID.randomUUID()))
              .andExpect(status().isNoContent());
      }
  }
  ```

- [ ] **Step 2: Run the test to verify it fails (compilation error)**

  Run: `./mvnw test -Dtest=BlobControllerTest -q`

  Expected: COMPILE ERROR — `BlobController` not found.

- [ ] **Step 3: Create `BlobController.java`**

  Create `src/main/java/io/k2dv/garden/blob/controller/BlobController.java`:

  ```java
  package io.k2dv.garden.blob.controller;

  import io.k2dv.garden.auth.security.HasPermission;
  import io.k2dv.garden.blob.dto.BlobResponse;
  import io.k2dv.garden.blob.service.BlobService;
  import io.k2dv.garden.shared.dto.ApiResponse;
  import lombok.RequiredArgsConstructor;
  import org.springframework.http.HttpStatus;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.DeleteMapping;
  import org.springframework.web.bind.annotation.PathVariable;
  import org.springframework.web.bind.annotation.PostMapping;
  import org.springframework.web.bind.annotation.RequestMapping;
  import org.springframework.web.bind.annotation.RequestParam;
  import org.springframework.web.bind.annotation.RestController;
  import org.springframework.web.multipart.MultipartFile;

  import java.util.UUID;

  @RestController
  @RequestMapping("/api/v1/admin/blobs")
  @RequiredArgsConstructor
  public class BlobController {

      private final BlobService blobService;

      @PostMapping(consumes = "multipart/form-data")
      @HasPermission("blob:upload")
      public ResponseEntity<ApiResponse<BlobResponse>> upload(@RequestParam("file") MultipartFile file) {
          BlobResponse resp = blobService.upload(file);
          return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
      }

      @DeleteMapping("/{id}")
      @HasPermission("blob:delete")
      public ResponseEntity<Void> delete(@PathVariable UUID id) {
          blobService.delete(id);
          return ResponseEntity.noContent().build();
      }
  }
  ```

- [ ] **Step 4: Update `GlobalExceptionHandler.java` to handle `MaxUploadSizeExceededException`**

  Add a new handler method to `src/main/java/io/k2dv/garden/shared/exception/GlobalExceptionHandler.java` — insert it between the `handleValidation` method and the `handleDomain` method:

  ```java
  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<ErrorResponse> handleMaxUploadSize(
          org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
      return ResponseEntity.badRequest()
          .body(ErrorResponse.builder()
              .error("FILE_TOO_LARGE")
              .message("File exceeds maximum upload size")
              .status(400)
              .build());
  }
  ```

- [ ] **Step 5: Run `BlobControllerTest` to verify it passes**

  Run: `./mvnw test -Dtest=BlobControllerTest`

  Expected: `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`

- [ ] **Step 6: Run the full test suite**

  Run: `./mvnw test`

  Expected: `Tests run: 57, Failures: 0, Errors: 0, Skipped: 0`

  (52 existing + 2 BlobServiceIT + 3 BlobControllerTest = 57)

- [ ] **Step 7: Commit**

  ```bash
  git add src/main/java/io/k2dv/garden/blob/controller/BlobController.java \
    src/main/java/io/k2dv/garden/shared/exception/GlobalExceptionHandler.java \
    src/test/java/io/k2dv/garden/blob/controller/BlobControllerTest.java
  git commit -m "feat(blob): add BlobController, BlobControllerTest, MaxUploadSize handler"
  ```
