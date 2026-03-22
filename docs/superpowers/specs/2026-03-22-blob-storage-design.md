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
    StorageProperties.java       # @ConfigurationProperties("storage")
    StorageConfig.java           # @Bean S3Client
  model/
    BlobObject.java              # JPA entity
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

---

## Data Model

### BlobObject entity

| Field       | Type      | Notes                                      |
|-------------|-----------|--------------------------------------------|
| id          | UUIDv7    | Primary key                                |
| key         | String    | Storage path, e.g. `uploads/01JQ5...-hero.jpg` |
| filename    | String    | Original upload filename                   |
| contentType | String    | MIME type                                  |
| size        | long      | Bytes                                      |
| createdAt   | Instant   | Set by BaseEntity                          |

No `url` column. Public URL is always computed: `resolveUrl(key)` = `{storage.base-url}/{key}`.

---

## Configuration

`StorageProperties` (`@ConfigurationProperties("storage")`):

| Property          | Default    | Description                        |
|-------------------|------------|------------------------------------|
| endpoint          | —          | S3-compatible endpoint URL         |
| bucket            | —          | Bucket name                        |
| accessKey         | —          | Access key ID                      |
| secretKey         | —          | Secret access key                  |
| baseUrl           | —          | Public base URL for resolving URLs |
| maxUploadSize     | 10485760   | Max upload size in bytes (10 MB)   |

Example for local MinIO:
```yaml
storage:
  endpoint: http://localhost:9000
  bucket: garden
  access-key: minioadmin
  secret-key: minioadmin
  base-url: http://localhost:9000/garden
  max-upload-size: 10485760
```

---

## StorageService Interface

```java
public interface StorageService {
    String store(String key, String contentType, InputStream data, long size);
    void delete(String key);
    String resolveUrl(String key);
}
```

`S3StorageService` implements this using `software.amazon.awssdk:s3`. The `S3Client` bean is constructed in `StorageConfig` using `endpointOverride` from `StorageProperties`, making it work with any S3-compatible backend.

---

## API Surface

### Upload

```
POST /api/v1/admin/blobs
Content-Type: multipart/form-data
Permission: blob:upload
```

Request: single `file` part.

Flow:
1. Validate `file.size` ≤ `maxUploadSize` — throw `ValidationException("FILE_TOO_LARGE", ...)` if exceeded
2. Generate key: `uploads/{uuidv7}-{sanitized-filename}` (sanitize = lowercase, strip non-alphanumeric except `.` and `-`)
3. Stream to S3 via `storageService.store(key, contentType, inputStream, size)`
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
- Mocks `BlobService`
- `POST /api/v1/admin/blobs` with valid file → 201 with correct JSON fields
- `POST /api/v1/admin/blobs` with oversized file → 400
- `DELETE /api/v1/admin/blobs/{id}` → 204

### BlobServiceIT (`@SpringBootTest`)
- Testcontainers MinIO (`minio/minio` Docker image) via `@ServiceConnection` or manual container config
- Upload a file → verify object exists in MinIO and `BlobObject` persisted in DB
- Delete the blob → verify object removed from MinIO and DB record gone

---

## Dependencies to Add

```xml
<!-- AWS SDK v2 S3 client -->
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

AWS SDK v2 version is managed via the `software.amazon.awssdk:bom` BOM, which should be added to `dependencyManagement`.
