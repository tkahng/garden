package io.k2dv.garden.giftcard.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.giftcard.dto.CreateGiftCardRequest;
import io.k2dv.garden.giftcard.dto.GiftCardFilter;
import io.k2dv.garden.giftcard.dto.GiftCardResponse;
import io.k2dv.garden.giftcard.dto.GiftCardTransactionRequest;
import io.k2dv.garden.giftcard.dto.GiftCardTransactionResponse;
import io.k2dv.garden.giftcard.dto.UpdateGiftCardRequest;
import io.k2dv.garden.giftcard.service.GiftCardService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.shared.dto.PagedResult;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin: Gift Cards", description = "Gift card management")
@RestController
@RequestMapping("/api/v1/admin/gift-cards")
@RequiredArgsConstructor
public class AdminGiftCardController {

    private final GiftCardService giftCardService;

    @GetMapping
    @HasPermission("gift_card:read")
    public ApiResponse<PagedResult<GiftCardResponse>> list(
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) String codeContains,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.of(giftCardService.list(
            new GiftCardFilter(isActive, codeContains),
            PageRequest.of(page, Math.min(size, 100))));
    }

    @GetMapping("/{id}")
    @HasPermission("gift_card:read")
    public ApiResponse<GiftCardResponse> getById(@PathVariable UUID id) {
        return ApiResponse.of(giftCardService.getById(id));
    }

    @GetMapping("/{id}/transactions")
    @HasPermission("gift_card:read")
    public ApiResponse<List<GiftCardTransactionResponse>> listTransactions(@PathVariable UUID id) {
        return ApiResponse.of(giftCardService.listTransactions(id));
    }

    @PostMapping
    @HasPermission("gift_card:write")
    public ResponseEntity<ApiResponse<GiftCardResponse>> create(@Valid @RequestBody CreateGiftCardRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(giftCardService.create(req)));
    }

    @PutMapping("/{id}")
    @HasPermission("gift_card:write")
    public ApiResponse<GiftCardResponse> update(
            @PathVariable UUID id,
            @RequestBody UpdateGiftCardRequest req) {
        return ApiResponse.of(giftCardService.update(id, req));
    }

    @PutMapping("/{id}/deactivate")
    @HasPermission("gift_card:delete")
    public ApiResponse<GiftCardResponse> deactivate(@PathVariable UUID id) {
        return ApiResponse.of(giftCardService.deactivate(id));
    }

    @PostMapping("/{id}/transactions")
    @HasPermission("gift_card:write")
    public ResponseEntity<ApiResponse<GiftCardTransactionResponse>> addTransaction(
            @PathVariable UUID id,
            @Valid @RequestBody GiftCardTransactionRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(giftCardService.addTransaction(id, req)));
    }
}
