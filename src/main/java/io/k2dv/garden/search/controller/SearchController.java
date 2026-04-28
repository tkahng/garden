package io.k2dv.garden.search.controller;

import io.k2dv.garden.search.dto.SearchResponse;
import io.k2dv.garden.search.service.SearchService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.exception.ValidationException;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/v1/search")
@RequiredArgsConstructor
@Tag(name = "Search", description = "Public storefront search across products, collections, articles, and pages")
@SecurityRequirements({})
public class SearchController {

    private static final Set<String> ALL_TYPES = Set.of("products", "collections", "articles", "pages");

    private final SearchService searchService;

    @GetMapping
    public ResponseEntity<ApiResponse<SearchResponse>> search(
            @RequestParam String q,
            @RequestParam(required = false) List<String> types,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int limit) {
        if (q == null || q.isBlank()) {
            throw new ValidationException("QUERY_REQUIRED", "Search query must not be blank");
        }
        int clampedLimit = Math.min(limit, 50);
        Set<String> resolvedTypes = (types == null || types.isEmpty()) ? ALL_TYPES : Set.copyOf(types);
        var pageable = PageRequest.of(page, clampedLimit, Sort.by("createdAt").descending());
        return ResponseEntity.ok(ApiResponse.of(searchService.search(q, resolvedTypes, pageable)));
    }
}
