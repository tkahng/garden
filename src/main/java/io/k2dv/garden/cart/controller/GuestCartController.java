package io.k2dv.garden.cart.controller;

import io.k2dv.garden.cart.dto.AddCartItemRequest;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.cart.dto.UpdateCartItemRequest;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Guest Cart", description = "Shopping cart for unauthenticated guests (X-Guest-Session header required)")
@RestController
@RequestMapping("/api/v1/guest-cart")
@RequiredArgsConstructor
@SecurityRequirements({})
public class GuestCartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart(
            @RequestHeader("X-Guest-Session") UUID sessionId) {
        return ResponseEntity.ok(ApiResponse.of(cartService.getOrCreateGuestCart(sessionId)));
    }

    @DeleteMapping
    public ResponseEntity<Void> abandonCart(
            @RequestHeader("X-Guest-Session") UUID sessionId) {
        cartService.abandonGuestCart(sessionId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<CartResponse>> addItem(
            @RequestHeader("X-Guest-Session") UUID sessionId,
            @Valid @RequestBody AddCartItemRequest req) {
        return ResponseEntity.ok(ApiResponse.of(cartService.addGuestItem(sessionId, req)));
    }

    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> updateItem(
            @RequestHeader("X-Guest-Session") UUID sessionId,
            @PathVariable UUID itemId,
            @Valid @RequestBody UpdateCartItemRequest req) {
        return ResponseEntity.ok(ApiResponse.of(cartService.updateGuestItem(sessionId, itemId, req)));
    }

    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<CartResponse>> removeItem(
            @RequestHeader("X-Guest-Session") UUID sessionId,
            @PathVariable UUID itemId) {
        return ResponseEntity.ok(ApiResponse.of(cartService.removeGuestItem(sessionId, itemId)));
    }
}
