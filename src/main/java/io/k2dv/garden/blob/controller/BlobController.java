package io.k2dv.garden.blob.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.blob.dto.BlobFilter;
import io.k2dv.garden.blob.dto.BlobResponse;
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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ApiResponse.of(blobService.list(
            new BlobFilter(contentType, filenameContains),
            PageRequest.of(page, clampedSize)));
    }

    @GetMapping("/{id}")
    @HasPermission("blob:read")
    public ApiResponse<BlobResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(blobService.getById(id));
    }

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
