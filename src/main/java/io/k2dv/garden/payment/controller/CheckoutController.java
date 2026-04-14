package io.k2dv.garden.payment.controller;

import io.k2dv.garden.auth.security.Authenticated;
import io.k2dv.garden.auth.security.CurrentUser;
import io.k2dv.garden.payment.dto.CheckoutRequest;
import io.k2dv.garden.payment.dto.CheckoutResponse;
import io.k2dv.garden.payment.dto.CheckoutReturnResponse;
import io.k2dv.garden.payment.service.PaymentService;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.user.model.User;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payments", description = "Checkout and payment webhook endpoints")
@RestController
@RequestMapping("/api/v1/checkout")
@RequiredArgsConstructor
@Authenticated
public class CheckoutController {

    private final PaymentService paymentService;

    @PostMapping
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @CurrentUser User user,
            @RequestBody(required = false) CheckoutRequest req) {
        String discountCode = req != null ? req.discountCode() : null;
        String giftCardCode = req != null ? req.giftCardCode() : null;
        return ResponseEntity.ok(ApiResponse.of(paymentService.initiateCheckout(user.getId(), discountCode, giftCardCode)));
    }

    @GetMapping("/return")
    public ResponseEntity<ApiResponse<CheckoutReturnResponse>> checkoutReturn(
            @CurrentUser User user,
            @RequestParam("session_id") String sessionId) {
        return ResponseEntity.ok(ApiResponse.of(paymentService.verifyReturn(sessionId, user.getId())));
    }
}
