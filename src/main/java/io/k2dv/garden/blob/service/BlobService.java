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
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PagedResult.of(blobRepo.findAll(spec, withSort),
            b -> BlobResponse.from(b, storageService.resolveUrl(b.getKey())));
    }

    @Transactional(readOnly = true)
    public BlobResponse getById(UUID id) {
        BlobObject blob = findOrThrow(id);
        return BlobResponse.from(blob, storageService.resolveUrl(blob.getKey()));
    }

    @Transactional
    public BlobResponse upload(MultipartFile file) {
        if (file.getSize() > storageProperties.getMaxUploadSize()) {
            throw new ValidationException("FILE_TOO_LARGE", "File exceeds maximum upload size");
        }
        String sanitized = sanitize(file.getOriginalFilename());
        String key = "uploads/" + UUID.randomUUID() + "-" + sanitized;
        String contentType = contentType(file);
        try {
            storageService.store(key, contentType, file.getInputStream(), file.getSize());
        } catch (IOException e) {
            throw new RuntimeException("Failed to read upload stream", e);
        }
        BlobObject blob = new BlobObject();
        blob.setKey(key);
        blob.setFilename(file.getOriginalFilename() != null ? file.getOriginalFilename() : "unknown");
        blob.setContentType(contentType);
        blob.setSize(file.getSize());

        if (IMAGE_TYPES.contains(contentType)) {
            try {
                BufferedImage img = ImageIO.read(file.getInputStream());
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

    @Transactional
    public BlobResponse updateMetadata(UUID id, UpdateBlobRequest req) {
        BlobObject blob = findOrThrow(id);
        blob.setAlt(req.alt());
        blob.setTitle(req.title());
        blob = blobRepo.saveAndFlush(blob);
        return BlobResponse.from(blob, storageService.resolveUrl(blob.getKey()));
    }

    @Transactional
    public void delete(UUID id) {
        BlobObject blob = findOrThrow(id);
        storageService.delete(blob.getKey());
        blobRepo.delete(blob);
    }

    @Transactional
    public void bulkDelete(List<UUID> ids) {
        List<BlobObject> blobs = blobRepo.findAllById(ids);
        for (BlobObject blob : blobs) {
            storageService.delete(blob.getKey());
        }
        blobRepo.deleteAll(blobs);
    }

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
