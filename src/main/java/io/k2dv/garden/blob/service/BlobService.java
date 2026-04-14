package io.k2dv.garden.blob.service;

import io.k2dv.garden.blob.config.StorageProperties;
import io.k2dv.garden.blob.dto.BlobFilter;
import io.k2dv.garden.blob.dto.BlobResponse;
import io.k2dv.garden.blob.model.BlobObject;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BlobService {

    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;
    private final StorageProperties storageProperties;

    @Transactional(readOnly = true)
    public PagedResult<BlobResponse> list(BlobFilter filter, Pageable pageable) {
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
        return PagedResult.of(blobRepo.findAll(spec, pageable),
            b -> BlobResponse.from(b, storageService.resolveUrl(b.getKey())));
    }

    @Transactional(readOnly = true)
    public BlobResponse getById(UUID id) {
        BlobObject blob = blobRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("BLOB_NOT_FOUND", "Blob not found"));
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
