package io.k2dv.garden.blob.service;

import io.k2dv.garden.blob.config.StorageProperties;
import io.k2dv.garden.blob.dto.*;
import io.k2dv.garden.blob.model.BlobObject;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.content.repository.ArticleImageRepository;
import io.k2dv.garden.product.repository.ProductImageRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Application-level service for managing uploaded file assets (blobs). Handles file validation,
 * upload to the configured storage backend, image dimension extraction, folder organisation,
 * and lifecycle operations such as replacement and deletion. Tracks where each blob is used
 * (product images, article images) so referential integrity can be inspected before deletion.
 */
@Service
@RequiredArgsConstructor
public class BlobService {

    private static final Set<String> IMAGE_TYPES = Set.of(
        "image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp", "image/tiff"
    );

    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final ProductImageRepository productImageRepo;
    private final ArticleImageRepository articleImageRepo;

    /**
     * Returns a filterable, sortable paginated list of all uploaded blobs, each decorated with
     * a resolved public URL. Supports filtering by content type, filename substring, and folder.
     */
    @Transactional(readOnly = true)
    public PagedResult<BlobResponse> list(BlobFilter filter, Pageable pageable) {
        Sort sort = resolveSort(filter);
        Pageable withSort = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

        Specification<BlobObject> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter != null) {
                if (filter.contentType() != null && !filter.contentType().isBlank()) {
                    predicates.add(cb.equal(root.get("contentType"), filter.contentType()));
                }
                if (filter.filenameContains() != null && !filter.filenameContains().isBlank()) {
                    predicates.add(cb.like(cb.lower(root.get("filename")),
                        "%" + filter.filenameContains().toLowerCase() + "%"));
                }
                if (filter.folder() != null && !filter.folder().isBlank()) {
                    predicates.add(cb.equal(root.get("folder"), filter.folder()));
                } else if (filter.unorganized()) {
                    predicates.add(cb.isNull(root.get("folder")));
                }
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PagedResult.of(blobRepo.findAll(spec, withSort),
            b -> BlobResponse.from(b, storageService.resolveUrl(b.getKey())));
    }

    /**
     * Fetches a single blob record by ID, resolving its storage key to a public URL.
     */
    @Transactional(readOnly = true)
    public BlobResponse getById(UUID id) {
        BlobObject blob = findOrThrow(id);
        return BlobResponse.from(blob, storageService.resolveUrl(blob.getKey()));
    }

    /**
     * Validates, stores, and registers an uploaded file. Image files have their dimensions
     * extracted at upload time for use in responsive rendering. Enforces the configured maximum
     * file size and sanitises the filename before writing to storage.
     */
    @Transactional
    public BlobResponse upload(MultipartFile file) {
        if (file.getSize() > storageProperties.getMaxUploadSize()) {
            throw new ValidationException("FILE_TOO_LARGE", "File exceeds maximum upload size");
        }
        String sanitized = sanitize(file.getOriginalFilename());
        String key = "uploads/" + UUID.randomUUID() + "-" + sanitized;
        String contentType = contentType(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload stream", e);
        }
        storageService.store(key, contentType, new ByteArrayInputStream(bytes), bytes.length);

        BlobObject blob = new BlobObject();
        blob.setKey(key);
        blob.setFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
        blob.setContentType(contentType);
        blob.setSize(file.getSize());

        if (IMAGE_TYPES.contains(contentType)) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    blob.setWidth(img.getWidth());
                    blob.setHeight(img.getHeight());
                }
            } catch (IOException ignored) {
            }
        }

        blob = blobRepo.saveAndFlush(blob);
        return BlobResponse.from(blob, storageService.resolveUrl(key));
    }

    /**
     * Overwrites the binary content of an existing blob at its current storage key, preserving
     * all existing references (product images, articles) while updating metadata and dimensions.
     */
    @Transactional
    public BlobResponse replace(UUID id, MultipartFile file) {
        if (file.getSize() > storageProperties.getMaxUploadSize()) {
            throw new ValidationException("FILE_TOO_LARGE", "File exceeds maximum upload size");
        }
        BlobObject blob = findOrThrow(id);
        String contentType = contentType(file);
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload stream", e);
        }
        storageService.store(blob.getKey(), contentType, new ByteArrayInputStream(bytes), bytes.length);

        blob.setFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : blob.getFilename());
        blob.setContentType(contentType);
        blob.setSize(file.getSize());
        blob.setWidth(null);
        blob.setHeight(null);

        if (IMAGE_TYPES.contains(contentType)) {
            try {
                BufferedImage img = ImageIO.read(new ByteArrayInputStream(bytes));
                if (img != null) {
                    blob.setWidth(img.getWidth());
                    blob.setHeight(img.getHeight());
                }
            } catch (IOException ignored) {
            }
        }

        blob = blobRepo.saveAndFlush(blob);
        return BlobResponse.from(blob, storageService.resolveUrl(blob.getKey()));
    }

    /**
     * Returns the distinct list of folder names in use, for populating folder-picker UIs
     * in the admin media library.
     */
    @Transactional(readOnly = true)
    public List<String> listFolders() {
        return blobRepo.findDistinctFolders();
    }

    /**
     * Returns aggregate storage statistics (total blob count and cumulative byte size) for
     * the admin dashboard.
     */
    @Transactional(readOnly = true)
    public BlobStatsResponse getStats() {
        Object[] row = blobRepo.findStats();
        long count = row[0] instanceof Number n ? n.longValue() : 0L;
        long bytes = row[1] instanceof Number n ? n.longValue() : 0L;
        return new BlobStatsResponse(count, bytes);
    }

    /**
     * Bulk-assigns a set of blobs to a named folder, or moves them to the root (unorganised)
     * when the folder argument is null or blank.
     */
    @Transactional
    public void moveToFolder(List<UUID> ids, String folder) {
        String target = (folder != null && !folder.isBlank()) ? folder.strip() : null;
        List<BlobObject> blobs = blobRepo.findAllById(ids);
        blobs.forEach(b -> b.setFolder(target));
        blobRepo.saveAll(blobs);
    }

    /**
     * Updates the descriptive metadata (alt text, title, folder) of a blob without touching
     * the stored binary. Used by the admin media library to improve SEO and accessibility.
     */
    @Transactional
    public BlobResponse updateMetadata(UUID id, UpdateBlobRequest req) {
        BlobObject blob = findOrThrow(id);
        blob.setAlt(req.alt());
        blob.setTitle(req.title());
        if (req.folder() != null) {
            blob.setFolder(req.folder().isBlank() ? null : req.folder().strip());
        }
        blob = blobRepo.saveAndFlush(blob);
        return BlobResponse.from(blob, storageService.resolveUrl(blob.getKey()));
    }

    /**
     * Permanently removes a blob from both the storage backend and the database. Callers should
     * check {@link #getUsages} first to avoid breaking product or article images.
     */
    @Transactional
    public void delete(UUID id) {
        BlobObject blob = findOrThrow(id);
        storageService.delete(blob.getKey());
        blobRepo.delete(blob);
    }

    /**
     * Bulk-deletes a list of blobs, removing each from storage before purging the database
     * records in a single transaction.
     */
    @Transactional
    public void bulkDelete(List<UUID> ids) {
        List<BlobObject> blobs = blobRepo.findAllById(ids);
        for (BlobObject blob : blobs) {
            storageService.delete(blob.getKey());
        }
        blobRepo.deleteAll(blobs);
    }

    /**
     * Enumerates every product image and article image that references this blob, enabling the
     * admin UI to warn before a destructive delete.
     */
    @Transactional(readOnly = true)
    public List<BlobUsageResponse> getUsages(UUID id) {
        findOrThrow(id);
        List<BlobUsageResponse> usages = new ArrayList<>();
        productImageRepo.findByBlobId(id).forEach(pi ->
            usages.add(new BlobUsageResponse("product", pi.getProductId())));
        articleImageRepo.findByBlobId(id).forEach(ai ->
            usages.add(new BlobUsageResponse("article", ai.getArticleId())));
        return usages;
    }

    private BlobObject findOrThrow(UUID id) {
        return blobRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("BLOB_NOT_FOUND", "Blob not found"));
    }

    private Sort resolveSort(BlobFilter filter) {
        if (filter == null || filter.sortBy() == null) {
            return Sort.by(Sort.Direction.DESC, "createdAt");
        }
        String field = switch (filter.sortBy()) {
            case "filename" -> "filename";
            case "size" -> "size";
            case "contentType" -> "contentType";
            default -> "createdAt";
        };
        Sort.Direction dir = "asc".equalsIgnoreCase(filter.sortDir())
            ? Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(dir, field);
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
