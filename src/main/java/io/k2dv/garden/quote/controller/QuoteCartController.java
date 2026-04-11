package io.k2dv.garden.quote.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.quote.dto.AddQuoteCartItemRequest;
import io.k2dv.garden.quote.dto.QuoteCartResponse;
import io.k2dv.garden.quote.dto.UpdateQuoteCartItemRequest;
import io.k2dv.garden.quote.service.QuoteCartService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Quote Cart", description = "Quote cart operations")
@RestController
@RequestMapping("/api/v1/quote-cart")
@RequiredArgsConstructor
@Authenticated
public class QuoteCartController {

    private final QuoteCartService quoteCartService;

    @GetMapping
    public ResponseEntity<ApiResponse<QuoteCartResponse>> getCart(@CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse.of(quoteCartService.getOrCreateActiveCart(user.getId())));
    }

    @DeleteMapping
    public ResponseEntity<Void> clearCart(@CurrentUser User user) {
        quoteCartService.clearCart(user.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<QuoteCartResponse>> addItem(
        @CurrentUser User user,
        @Valid @RequestBody AddQuoteCartItemRequest req) {
        return ResponseEntity.ok(ApiResponse.of(quoteCartService.addItem(user.getId(), req)));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<QuoteCartResponse>> updateItem(
        @CurrentUser User user,
        @PathVariable UUID itemId,
        @Valid @RequestBody UpdateQuoteCartItemRequest req) {
        return ResponseEntity.ok(ApiResponse.of(quoteCartService.updateItem(user.getId(), itemId, req)));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<QuoteCartResponse>> removeItem(
        @CurrentUser User user,
        @PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.of(quoteCartService.removeItem(user.getId(), itemId)));
    }
}
