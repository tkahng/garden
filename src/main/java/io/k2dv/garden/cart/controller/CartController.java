package io.k2dv.garden.cart.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.cart.dto.AddCartItemRequest;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.cart.dto.SetCartCompanyRequest;
import io.k2dv.garden.cart.dto.UpdateCartItemRequest;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Cart", description = "Shopping cart operations")
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Authenticated
public class CartController {

  private final CartService cartService;

  @GetMapping
  public ResponseEntity<ApiResponse<CartResponse>> getCart(@CurrentUser User user) {
    return ResponseEntity.ok(ApiResponse.of(cartService.getOrCreateActiveCart(user.getId())));
  }

  @DeleteMapping
  public ResponseEntity<Void> abandonCart(@CurrentUser User user) {
    cartService.abandonCart(user.getId());
    return ResponseEntity.noContent().build();
  }

  @PostMapping("/items")
  public ResponseEntity<ApiResponse<CartResponse>> addItem(
      @CurrentUser User user,
      @Valid @RequestBody AddCartItemRequest req) {
    return ResponseEntity.ok(ApiResponse.of(cartService.addItem(user.getId(), req)));
  }

  @PutMapping("/items/{itemId}")
  public ResponseEntity<ApiResponse<CartResponse>> updateItem(
      @CurrentUser User user,
      @PathVariable UUID itemId,
      @Valid @RequestBody UpdateCartItemRequest req) {
    return ResponseEntity.ok(ApiResponse.of(cartService.updateItem(user.getId(), itemId, req)));
  }

  @DeleteMapping("/items/{itemId}")
  public ResponseEntity<ApiResponse<CartResponse>> removeItem(
      @CurrentUser User user,
      @PathVariable UUID itemId) {
    return ResponseEntity.ok(ApiResponse.of(cartService.removeItem(user.getId(), itemId)));
  }

  @PutMapping("/company")
  public ResponseEntity<ApiResponse<CartResponse>> setCompany(
      @CurrentUser User user,
      @Valid @RequestBody SetCartCompanyRequest req) {
    return ResponseEntity.ok(ApiResponse.of(cartService.setCompanyContext(user.getId(), req.companyId())));
  }

  @DeleteMapping("/company")
  public ResponseEntity<ApiResponse<CartResponse>> clearCompany(@CurrentUser User user) {
    return ResponseEntity.ok(ApiResponse.of(cartService.clearCompanyContext(user.getId())));
  }
}
