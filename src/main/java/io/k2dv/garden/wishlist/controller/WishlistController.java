package io.k2dv.garden.wishlist.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.wishlist.dto.AddWishlistItemRequest;
import io.k2dv.garden.wishlist.dto.WishlistResponse;
import io.k2dv.garden.wishlist.service.WishlistService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/account/wishlist")
@RequiredArgsConstructor
@Tag(name = "Wishlist", description = "Customer wishlist management")
@Authenticated
public class WishlistController {

    private final WishlistService wishlistService;

    @GetMapping
    public ResponseEntity<ApiResponse<WishlistResponse>> getWishlist(@CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse.of(wishlistService.getWishlist(user.getId())));
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<WishlistResponse>> addItem(
            @CurrentUser User user,
            @Valid @RequestBody AddWishlistItemRequest req) {
        WishlistResponse response = wishlistService.addItem(user.getId(), req.productId());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.of(response));
    }

    @DeleteMapping("/items/{productId}")
    public ResponseEntity<ApiResponse<WishlistResponse>> removeItem(
            @CurrentUser User user,
            @PathVariable UUID productId) {
        return ResponseEntity.ok(ApiResponse.of(wishlistService.removeItem(user.getId(), productId)));
    }
}
