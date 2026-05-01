package io.k2dv.garden.payment.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.payment.dto.CheckoutRequest;
import io.k2dv.garden.payment.dto.CheckoutResponse;
import io.k2dv.garden.payment.dto.CheckoutReturnResponse;
import io.k2dv.garden.payment.dto.GuestCheckoutRequest;
import io.k2dv.garden.payment.service.PaymentService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Tag(name = "Payments", description = "Checkout and payment webhook endpoints")
@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
public class CheckoutController {

    private final PaymentService paymentService;

    @Authenticated
    @PostMapping
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @CurrentUser User user,
            @RequestBody(required = false) CheckoutRequest req) {
        String discountCode = req != null ? req.discountCode() : null;
        String giftCardCode = req != null ? req.giftCardCode() : null;
        UUID shippingRateId = req != null ? req.shippingRateId() : null;
        String poNumber = req != null ? req.poNumber() : null;
        return ResponseEntity.ok(ApiResponse.of(
                paymentService.initiateCheckout(user.getId(), discountCode, giftCardCode, shippingRateId, poNumber)));
    }

    @SecurityRequirements({})
    @PostMapping("/guest")
    public ResponseEntity<ApiResponse<CheckoutResponse>> guestCheckout(
            @RequestHeader("X-Guest-Session") UUID sessionId,
            @Valid @RequestBody GuestCheckoutRequest req) {
        return ResponseEntity.ok(ApiResponse.of(
                paymentService.initiateGuestCheckout(
                        req.email(), req.shippingAddress(), req.shippingRateId(),
                        req.discountCode(), req.giftCardCode(), sessionId, req.poNumber())));
    }

    @SecurityRequirements({})
    @GetMapping("/return")
    public ResponseEntity<ApiResponse<CheckoutReturnResponse>> checkoutReturn(
            @RequestParam("session_id") String sessionId) {
        UUID userId = resolveCurrentUserId();
        return ResponseEntity.ok(ApiResponse.of(paymentService.verifyReturn(sessionId, userId)));
    }

    private UUID resolveCurrentUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken jwt) {
            return UUID.fromString(jwt.getToken().getSubject());
        }
        return null;
    }
}
