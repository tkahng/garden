package io.k2dv.garden.order.template.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.order.template.dto.CreateOrderTemplateRequest;
import io.k2dv.garden.order.template.dto.OrderTemplateResponse;
import io.k2dv.garden.order.template.service.OrderTemplateService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Tag(name = "Storefront: Order Templates", description = "Saved cart templates for repeat ordering")
@RestController
@RequestMapping("/api/v1/storefront/order-templates")
@RequiredArgsConstructor
@Authenticated
public class StorefrontOrderTemplateController {

    private final OrderTemplateService templateService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<OrderTemplateResponse>>> list(@CurrentUser User user) {
        return ResponseEntity.ok(ApiResponse.of(templateService.listForUser(user.getId())));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<OrderTemplateResponse>> create(
            @CurrentUser User user,
            @Valid @RequestBody CreateOrderTemplateRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(templateService.create(user.getId(), req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<OrderTemplateResponse>> get(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(templateService.getById(user.getId(), id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@CurrentUser User user, @PathVariable UUID id) {
        templateService.delete(user.getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/load")
    public ResponseEntity<ApiResponse<CartResponse>> load(
            @CurrentUser User user,
            @PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.of(templateService.loadToCart(user.getId(), id)));
    }
}
