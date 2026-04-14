package io.k2dv.garden.blob.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.blob.dto.*;
import io.k2dv.garden.blob.service.BlobService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Tag(name = "Blobs", description = "File upload and storage")
@RestController
@RequestMapping("/api/v1/admin/blobs")
@RequiredArgsConstructor
public class BlobController {

    private final BlobService blobService;

    @GetMapping
    @HasPermission("blob:read")
    public ApiResponse<PagedResult<BlobResponse>> list(
            @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String filenameContains,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDir,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ApiResponse.of(blobService.list(
            new BlobFilter(contentType, filenameContains, sortBy, sortDir),
            PageRequest.of(page, clampedSize)));
    }

    @GetMapping("/{id}")
    @HasPermission("blob:read")
    public ApiResponse<BlobResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(blobService.getById(id));
    }

    @GetMapping("/{id}/usages")
    @HasPermission("blob:read")
    public ApiResponse<List<BlobUsageResponse>> getUsages(@PathVariable UUID id) {
        return ApiResponse.of(blobService.getUsages(id));
    }

    @PostMapping(consumes = "multipart/form-data")
    @HasPermission("blob:upload")
    public ResponseEntity<ApiResponse<BlobResponse>> upload(@RequestParam("file") MultipartFile file) {
        BlobResponse resp = blobService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(resp));
    }

    @PatchMapping("/{id}")
    @HasPermission("blob:update")
    public ApiResponse<BlobResponse> update(@PathVariable UUID id,
                                             @RequestBody UpdateBlobRequest req) {
        return ApiResponse.of(blobService.updateMetadata(id, req));
    }

    @DeleteMapping("/{id}")
    @HasPermission("blob:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        blobService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping
    @HasPermission("blob:delete")
    public ResponseEntity<Void> bulkDelete(@RequestBody BulkDeleteRequest req) {
        blobService.bulkDelete(req.ids());
        return ResponseEntity.noContent().build();
    }
}
