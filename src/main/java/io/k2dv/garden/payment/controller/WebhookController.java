package io.k2dv.garden.payment.controller;

import io.k2dv.garden.payment.config.StripeProperties;
import io.k2dv.garden.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/webhooks")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Checkout and payment webhook endpoints")
@SecurityRequirements({})
public class WebhookController {

    private final PaymentService paymentService;
    private final StripeProperties stripeProperties;

    @PostMapping("/stripe")
    public ResponseEntity<Void> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        paymentService.handleWebhook(payload, sigHeader, stripeProperties.getWebhookSecret());
        return ResponseEntity.ok().build();
    }
}
