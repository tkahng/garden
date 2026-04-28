package io.k2dv.garden.content.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.model.PageStatus;
import io.k2dv.garden.content.service.PageService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Content", description = "Admin page management")
@RestController
@RequestMapping("/api/v1/admin/pages")
@RequiredArgsConstructor
public class AdminPageController {

    private final PageService pageService;

    @PostMapping
    @HasPermission("content:write")
    public ResponseEntity<ApiResponse<AdminPageResponse>> create(@Valid @RequestBody CreatePageRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(pageService.create(req)));
    }

    @GetMapping
    @HasPermission("content:read")
    public ResponseEntity<ApiResponse<PagedResult<AdminPageResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) PageStatus status,
            @RequestParam(required = false) String titleContains,
            @RequestParam(required = false) String handleContains,
            @RequestParam(required = false) String q) {
        var filter = new PageFilterRequest(status, titleContains, handleContains, q);
        var pageable = PageRequest.of(page, pageSize, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.of(pageService.list(filter, pageable)));
    }

    @GetMapping("/{id}")
    @HasPermission("content:read")
    public ResponseEntity<ApiResponse<AdminPageResponse>> get(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(pageService.get(id)));
    }

    @PutMapping("/{id}")
    @HasPermission("content:write")
    public ResponseEntity<ApiResponse<AdminPageResponse>> update(
            @PathVariable UUID id, @Valid @RequestBody UpdatePageRequest req) {
        return ResponseEntity.ok(ApiResponse.of(pageService.update(id, req)));
    }

    @PatchMapping("/{id}/status")
    @HasPermission("content:publish")
    public ResponseEntity<ApiResponse<AdminPageResponse>> changeStatus(
            @PathVariable UUID id, @Valid @RequestBody PageStatusRequest req) {
        return ResponseEntity.ok(ApiResponse.of(pageService.changeStatus(id, req)));
    }

    @DeleteMapping("/{id}")
    @HasPermission("content:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        pageService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
