package io.k2dv.garden.blob.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.blob.dto.BlobResponse;
import io.k2dv.garden.blob.service.BlobService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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

@Tag(name = "Blobs", description = "File upload and storage")
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
