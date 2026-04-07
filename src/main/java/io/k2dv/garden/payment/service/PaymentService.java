package io.k2dv.garden.payment.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import io.k2dv.garden.cart.model.Cart;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.payment.dto.CheckoutResponse;
import io.k2dv.garden.payment.dto.CheckoutReturnResponse;
import io.k2dv.garden.payment.exception.PaymentException;
import io.k2dv.garden.payment.gateway.StripeGateway;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final CartService cartService;
    private final OrderService orderService;
    private final StripeGateway stripeGateway;
    private final ProductVariantRepository variantRepo;
    private final AppProperties appProperties;

    // NOT @Transactional — Stripe call is outside transaction; each sub-call manages its own tx
    public CheckoutResponse initiateCheckout(UUID userId) {
        Cart cart = cartService.requireActiveCart(userId);
        List<CartItem> cartItems = cartService.getCartItems(cart.getId());

        if (cartItems.isEmpty()) {
            throw new ValidationException("EMPTY_CART", "Cart has no items");
        }

        Order order = orderService.createFromCart(userId, cartItems);

        try {
            String currency = order.getCurrency() != null ? order.getCurrency() : "usd";

            SessionCreateParams.Builder builder = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(appProperties.getFrontendUrl()
                    + "/checkout/return?session_id={CHECKOUT_SESSION_ID}")
                // Both success and cancel redirect to the same return page; the page checks session status
                .setCancelUrl(appProperties.getFrontendUrl()
                    + "/checkout/return?session_id={CHECKOUT_SESSION_ID}")
                .putMetadata("orderId", order.getId() != null ? order.getId().toString() : "");

            for (CartItem cartItem : cartItems) {
                ProductVariant variant = variantRepo.findById(cartItem.getVariantId())
                    .orElseThrow(() -> new NotFoundException("VARIANT_NOT_FOUND",
                        "Variant not found: " + cartItem.getVariantId()));
                long unitAmountCents = cartItem.getUnitPrice()
                    .multiply(BigDecimal.valueOf(100))
                    .setScale(0, RoundingMode.HALF_UP)
                    .longValueExact();

                builder.addLineItem(
                    SessionCreateParams.LineItem.builder()
                        .setQuantity((long) cartItem.getQuantity())
                        .setPriceData(
                            SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency(currency)
                                .setUnitAmount(unitAmountCents)
                                .setProductData(
                                    SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(variant.getTitle())
                                        .build()
                                )
                                .build()
                        )
                        .build()
                );
            }

            Session session = stripeGateway.createCheckoutSession(builder.build());
            orderService.setStripeSession(order.getId(), session.getId());
            cartService.markCheckedOut(cart.getId());

            return new CheckoutResponse(session.getUrl(), order.getId());

        } catch (StripeException e) {
            orderService.cancelOrder(order.getId());
            throw new PaymentException("STRIPE_ERROR",
                "Failed to create checkout session: " + e.getMessage());
        }
    }

    public CheckoutReturnResponse verifyReturn(String stripeSessionId, UUID userId) {
        Order order = orderService.findByStripeSessionId(stripeSessionId);
        if (!order.getUserId().equals(userId)) {
            throw new ValidationException("ORDER_NOT_OWNED", "Order does not belong to current user");
        }
        return new CheckoutReturnResponse(order.getId(), order.getStatus());
    }

    public void handleWebhook(String payload, String sigHeader, String webhookSecret) {
        Event event;
        try {
            event = stripeGateway.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            throw new ValidationException("INVALID_WEBHOOK_SIGNATURE", "Invalid Stripe webhook signature");
        }

        switch (event.getType()) {
            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new PaymentException("STRIPE_DESERIALIZE_ERROR",
                        "Cannot deserialize Stripe session"));
                orderService.confirmPayment(session.getId(), session.getPaymentIntent());
            }
            case "checkout.session.expired" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                    .getObject()
                    .orElseThrow(() -> new PaymentException("STRIPE_DESERIALIZE_ERROR",
                        "Cannot deserialize Stripe session"));
                orderService.cancelBySession(session.getId());
            }
            default -> { /* ignore other event types */ }
        }
    }
}
