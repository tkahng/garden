package io.k2dv.garden.discount.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.discount.dto.CreateDiscountRequest;
import io.k2dv.garden.discount.dto.DiscountFilter;
import io.k2dv.garden.discount.dto.DiscountResponse;
import io.k2dv.garden.discount.dto.UpdateDiscountRequest;
import io.k2dv.garden.discount.model.DiscountType;
import io.k2dv.garden.discount.service.DiscountService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Admin: Discounts", description = "Admin discount management")
@RestController
@RequestMapping("/api/v1/admin/discounts")
@RequiredArgsConstructor
public class AdminDiscountController {

    private final DiscountService discountService;

    @GetMapping
    @HasPermission("discount:read")
    public ApiResponse<PagedResult<DiscountResponse>> list(
            @RequestParam(required = false) DiscountType type,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String codeContains,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        int clampedSize = Math.min(size, 100);
        return ApiResponse.of(discountService.list(
            new DiscountFilter(type, isActive, codeContains),
            PageRequest.of(page, clampedSize)));
    }

    @GetMapping("/{id}")
    @HasPermission("discount:read")
    public ApiResponse<DiscountResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(discountService.getById(id));
    }

    @PostMapping
    @HasPermission("discount:write")
    public ResponseEntity<ApiResponse<DiscountResponse>> create(@Valid @RequestBody CreateDiscountRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(discountService.create(req)));
    }

    @PutMapping("/{id}")
    @HasPermission("discount:write")
    public ApiResponse<DiscountResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateDiscountRequest req) {
        return ApiResponse.of(discountService.update(id, req));
    }

    @DeleteMapping("/{id}")
    @HasPermission("discount:delete")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        discountService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
