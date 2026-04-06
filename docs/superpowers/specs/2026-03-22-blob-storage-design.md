# Blob Storage Design Spec

## Overview

The `blob` package is an infrastructure domain that owns all object storage concerns. It provides a `BlobObject` entity, a `StorageService` interface, an AWS SDK v2 S3-compatible implementation, and a thin REST API for admin uploads and deletes. No other domain calls a storage service directly — they delegate to `BlobService`.

**Storage backends:**
- Local dev/test: MinIO (Docker Compose)
- Production: Cloudflare R2

One implementation (`S3StorageService`) serves all environments. Only configuration changes between environments.

---

## Package Structure

```
io.k2dv.garden.blob/
  config/
    StorageProperties.java       # @ConfigurationProperties("storage") + @Validated
    StorageConfig.java           # @Configuration + @EnableConfigurationProperties(StorageProperties.class) + @Bean S3Client
  model/
    BlobObject.java              # JPA entity (extends BaseEntity)
  repository/
    BlobObjectRepository.java
  service/
    StorageService.java          # interface: store / delete / resolveUrl
    S3StorageService.java        # AWS SDK v2 implementation
    BlobService.java             # orchestrates upload/delete, owns BlobObject lifecycle
  dto/
    BlobResponse.java            # id, key, filename, contentType, size, url
  controller/
    BlobController.java          # POST /api/v1/admin/blobs, DELETE /api/v1/admin/blobs/{id}
```

`StorageConfig` carries both `@EnableConfigurationProperties(StorageProperties.class)` and the `@Bean S3Client`, following the same pattern as `AppPropertiesConfig` (dedicated config class registers the properties bean).

---

## Data Model

### BlobObject entity

`BlobObject` extends `BaseEntity`, which provides `id` (UUIDv7), `createdAt`, and `updatedAt`. The migration must include the `updated_at` column and its trigger (same pattern as all other entity migrations in this project).

| Field       | Type      | Notes                                           |
|-------------|-----------|-------------------------------------------------|
| id          | UUIDv7    | Primary key (from BaseEntity)                   |
| key         | String    | Storage path, e.g. `uploads/01JQ5...-hero.jpg`  |
| filename    | String    | Original upload filename                        |
| contentType | String    | MIME type                                       |
| size        | long      | Bytes                                           |
| createdAt   | Instant   | From BaseEntity, set on insert                  |
| updatedAt   | Instant   | From BaseEntity, maintained by DB trigger       |

No `url` column. Public URL is always computed: `resolveUrl(key)` = `{storage.base-url}/{key}`.

### Flyway migration

**`V8__create_blob_objects.sql`** — table DDL + permission seed:

```sql
CREATE TABLE blob_objects (
    id           UUID        NOT NULL DEFAULT gen_random_uuid() PRIMARY KEY,
    key          TEXT        NOT NULL UNIQUE,  -- UNIQUE guards against duplicate storage paths
    filename     TEXT        NOT NULL,
    content_type TEXT        NOT NULL,
    size         BIGINT      NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()
);

CREATE TRIGGER set_updated_at
    BEFORE UPDATE ON blob_objects
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Seed blob permissions (static UUIDs following the 00000000-0000-7000-8000-000000000XXX pattern)
INSERT INTO permissions (id, name, resource, action, created_at, updated_at) VALUES
    ('00000000-0000-7000-8000-000000000025', 'blob:upload', 'blob', 'upload', clock_timestamp(), clock_timestamp()),
    ('00000000-0000-7000-8000-000000000026', 'blob:delete', 'blob', 'delete', clock_timestamp(), clock_timestamp())
ON CONFLICT (name) DO NOTHING;

-- Assign to OWNER and MANAGER
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id
FROM roles r, permissions p
WHERE r.name IN ('OWNER', 'MANAGER')
  AND p.name IN ('blob:upload', 'blob:delete')
ON CONFLICT DO NOTHING;
```

---

## Configuration

### StorageProperties

`StorageProperties` is annotated `@ConfigurationProperties("storage")` and `@Validated`. All required fields carry `@NotBlank` to fail fast at startup if misconfigured, matching the existing `AppProperties` pattern.

| Property          | Java type | Default    | Description                                |
|-------------------|-----------|------------|--------------------------------------------|
| endpoint          | String    | —          | `@NotBlank` S3-compatible endpoint URL     |
| bucket            | String    | —          | `@NotBlank` Bucket name                    |
| accessKey         | String    | —          | `@NotBlank` Access key ID                  |
| secretKey         | String    | —          | `@NotBlank` Secret access key              |
| baseUrl           | String    | —          | `@NotBlank` Public base URL for URLs       |
| maxUploadSize     | long      | 10485760   | Max upload size in bytes (10 MB)           |

Example for local MinIO (`application-local.yml`):
```yaml
storage:
  endpoint: http://localhost:9000
  bucket: garden
  access-key: minioadmin
  secret-key: minioadmin
  base-url: http://localhost:9000/garden
  max-upload-size: 10485760
```

### Spring multipart limits

Spring MVC rejects multipart requests larger than `spring.servlet.multipart.max-file-size` (default 1 MB) before the controller is invoked, producing a `MaxUploadSizeExceededException` rather than the application-level `ValidationException`. Set this higher than `maxUploadSize` so the application-level check always fires first:

```yaml
# application.yml
spring:
  servlet:
    multipart:
      max-file-size: 50MB
      max-request-size: 50MB
```

`GlobalExceptionHandler` must also add an explicit handler for `MaxUploadSizeExceededException` returning HTTP 400. Without it, oversized requests that bypass the application-level check (e.g., from clients ignoring the configured `maxUploadSize`) return HTTP 500 via the generic fallback handler.

```java
@ExceptionHandler(MaxUploadSizeExceededException.class)
ResponseEntity<ApiResponse<Void>> handleMaxUploadSize(MaxUploadSizeExceededException ex) {
    return ResponseEntity.badRequest().body(
        ApiResponse.error("FILE_TOO_LARGE", "File exceeds maximum upload size"));
}
```

**Note:** `spring.servlet.multipart.max-file-size=20MB` and `max-request-size=25MB` are already set in `application.properties` — no change needed. The existing 20 MB limit is well above the 10 MB application default, ensuring the application-level `ValidationException` check always fires first.

### Test properties

All `@SpringBootTest` contexts (including existing `AccountServiceIT`, `AuthServiceIT`, etc.) will fail to start after this plan is merged unless `StorageProperties` fields are satisfied. Add dummy storage properties to `src/test/resources/application-test.properties` (the existing test profile file):

```properties
# Dummy storage config — overridden by BlobServiceIT @DynamicPropertySource
storage.endpoint=http://localhost:9000
storage.bucket=test
storage.access-key=test
storage.secret-key=test
storage.base-url=http://localhost:9000/test
```

`BlobServiceIT` overrides these with its `@DynamicPropertySource` pointing to the live MinIO container. All other tests use the dummy values, which are never actually invoked.

---

## StorageService Interface

```java
public interface StorageService {
    void store(String key, String contentType, InputStream data, long size);
    void delete(String key);
    String resolveUrl(String key);
}
```

`store` returns `void` — the key is generated by the caller before invoking `store`, so there is nothing to return. `S3StorageService` implements this using `software.amazon.awssdk:s3`. The `S3Client` bean is constructed in `StorageConfig` using `endpointOverride` from `StorageProperties`.

---

## Exception: ValidationException

A new `ValidationException` must be added to `io.k2dv.garden.shared.exception`:

```java
public class ValidationException extends DomainException {
    public ValidationException(String code, String message) {
        super(code, message, HttpStatus.BAD_REQUEST);
    }
}
```

`GlobalExceptionHandler` already handles `DomainException` subclasses by their declared `HttpStatus`, so this automatically returns HTTP 400.

---

## API Surface

### Upload

```
POST /api/v1/admin/blobs
Content-Type: multipart/form-data
Permission: blob:upload
```

Request: single `file` part (`MultipartFile`).

Flow:
1. Validate `file.size` ≤ `maxUploadSize` — throw `new ValidationException("FILE_TOO_LARGE", "File exceeds maximum upload size")` if exceeded
2. Generate key: `uploads/{uuidv7}-{sanitized-filename}` (sanitize = lowercase, strip non-alphanumeric except `.` and `-`; applies to the filename portion only, not the `uploads/` prefix)
3. Call `storageService.store(key, contentType, inputStream, size)`
4. Persist `BlobObject` to DB
5. Return `201 Created` with `BlobResponse`

### Delete

```
DELETE /api/v1/admin/blobs/{id}
Permission: blob:delete
```

Flow:
1. Look up `BlobObject` by id — throw `NotFoundException` if not found
2. Call `storageService.delete(key)`
3. Delete DB record
4. Return `204 No Content`

**Note:** Referential integrity check (prevent delete if blob is referenced by a product image, etc.) is deferred until the product catalog domain is implemented.

**Future:** `GET /api/v1/admin/blobs` (paginated list) is deferred until the product catalog plan when blob browsing is needed.

---

## Response DTO

```java
public record BlobResponse(
    UUID id,
    String key,
    String filename,
    String contentType,
    long size,
    String url          // computed: baseUrl + "/" + key
) {}
```

---

## Testing

### BlobControllerTest (`@WebMvcTest`)

```java
@WebMvcTest(controllers = BlobController.class)
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
class BlobControllerTest {
    @Autowired MockMvc mvc;
    @MockitoBean BlobService blobService;
}
```

`TestSecurityConfig` sets `prePostEnabled = false`, so `@HasPermission` is not enforced in the slice — 401/403 path coverage is deferred (no auth infrastructure in the slice). Tests:
- `POST /api/v1/admin/blobs` with valid file → 201 with correct JSON fields
- `POST /api/v1/admin/blobs` with oversized file → 400
- `DELETE /api/v1/admin/blobs/{id}` → 204

### BlobServiceIT (`@SpringBootTest`)

Extends `AbstractIntegrationTest` (which provides the shared PostgreSQL Testcontainer). A static `MinIOContainer` is started in a `static {}` initializer — **not** inside `@DynamicPropertySource` and **not** via `@ServiceConnection` — matching the established pattern in `AbstractIntegrationTest`.

Because `BlobServiceIT` registers additional `storage.*` properties via `@DynamicPropertySource`, its Spring application context is cached separately from other `AbstractIntegrationTest` subclasses. This is expected.

MinIO state is not rolled back by `@Transactional`. Any test that uploads an object must clean it up in `@AfterEach` via `storageService.delete(key)`.

```java
@SuppressWarnings("deprecation")
static MinIOContainer minio = new MinIOContainer("minio/minio:latest");

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

@AfterEach
void cleanupMinio() {
    // call storageService.delete(key) for any object uploaded during the test
}
```

Tests:
- Upload a file → verify `BlobObject` persisted in DB and object retrievable from MinIO
- Delete the blob → verify DB record removed and object no longer in MinIO

---

## Dependencies to Add

The Spring Boot 4.x BOM manages `org.testcontainers:minio` via the Testcontainers BOM it imports — no explicit version needed.

```xml
<!-- pom.xml dependencyManagement — add AWS SDK v2 BOM -->
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

<!-- pom.xml dependencies -->
<dependency>
  <groupId>software.amazon.awssdk</groupId>
  <artifactId>s3</artifactId>
</dependency>

<!-- Testcontainers MinIO (test scope) -->
<dependency>
  <groupId>org.testcontainers</groupId>
  <artifactId>minio</artifactId>
  <scope>test</scope>
</dependency>
```
