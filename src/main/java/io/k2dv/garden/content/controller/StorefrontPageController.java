package io.k2dv.garden.content.controller;

import io.k2dv.garden.content.dto.*;
import io.k2dv.garden.content.service.PageService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/pages")
@RequiredArgsConstructor
public class StorefrontPageController {

    private final PageService pageService;

    @GetMapping
    public ResponseEntity<ApiResponse<PagedResult<PageResponse>>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String q) {
        var filter = new PageFilterRequest(null, null, null, q);
        var pageable = PageRequest.of(page, pageSize, Sort.by("publishedAt").descending());
        return ResponseEntity.ok(ApiResponse.of(pageService.listPublished(filter, pageable)));
    }

    @GetMapping("/{handle}")
    public ResponseEntity<ApiResponse<PageResponse>> getByHandle(@PathVariable String handle) {
        return ResponseEntity.ok(ApiResponse.of(pageService.getByHandle(handle)));
    }
}
