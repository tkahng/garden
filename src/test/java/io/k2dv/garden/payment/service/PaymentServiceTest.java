package io.k2dv.garden.payment.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import io.k2dv.garden.cart.model.Cart;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.cart.model.CartStatus;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock CartService cartService;
    @Mock OrderService orderService;
    @Mock StripeGateway stripeGateway;
    @Mock ProductVariantRepository variantRepo;
    @Mock AppProperties appProperties;

    PaymentService paymentService;

    @BeforeEach
    void setUp() {
        when(appProperties.getFrontendUrl()).thenReturn("http://localhost:3000");
        paymentService = new PaymentService(cartService, orderService, stripeGateway, variantRepo, appProperties);
    }

    private Cart stubCart(UUID cartId, UUID userId) {
        Cart cart = new Cart();
        cart.setUserId(userId);
        cart.setStatus(CartStatus.ACTIVE);
        return cart;
    }

    private CartItem stubCartItem(UUID variantId) {
        CartItem item = new CartItem();
        item.setVariantId(variantId);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("49.99"));
        return item;
    }

    private Order stubOrder(UUID orderId, UUID userId) {
        Order order = new Order();
        order.setUserId(userId);
        order.setStatus(OrderStatus.PENDING_PAYMENT);
        order.setTotalAmount(new BigDecimal("99.98"));
        return order;
    }

    @Test
    void initiateCheckout_happyPath_returnsCheckoutUrl() throws StripeException {
        UUID userId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Cart cart = stubCart(UUID.randomUUID(), userId);
        CartItem cartItem = stubCartItem(variantId);
        Order order = stubOrder(UUID.randomUUID(), userId);

        ProductVariant variant = new ProductVariant();
        variant.setTitle("Small / Green");
        variant.setPrice(new BigDecimal("49.99"));

        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test_123");
        when(session.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_123");

        when(cartService.requireActiveCart(userId)).thenReturn(cart);
        when(cartService.getCartItems(any())).thenReturn(List.of(cartItem));
        when(orderService.createFromCart(eq(userId), any())).thenReturn(order);
        when(variantRepo.findById(variantId)).thenReturn(Optional.of(variant));
        when(stripeGateway.createCheckoutSession(any())).thenReturn(session);

        CheckoutResponse response = paymentService.initiateCheckout(userId);

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_123");
        assertThat(response.orderId()).isEqualTo(order.getId());

        verify(orderService).setStripeSession(order.getId(), "cs_test_123");
        verify(cartService).markCheckedOut(cart.getId());
    }

    @Test
    void initiateCheckout_stripeFailure_cancelsOrderAndThrows() throws StripeException {
        UUID userId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Cart cart = stubCart(UUID.randomUUID(), userId);
        Order order = stubOrder(UUID.randomUUID(), userId);

        ProductVariant variant = new ProductVariant();
        variant.setTitle("Large");
        variant.setPrice(new BigDecimal("20.00"));

        when(cartService.requireActiveCart(userId)).thenReturn(cart);
        when(cartService.getCartItems(any())).thenReturn(List.of(stubCartItem(variantId)));
        when(orderService.createFromCart(eq(userId), any())).thenReturn(order);
        when(variantRepo.findById(variantId)).thenReturn(Optional.of(variant));
        when(stripeGateway.createCheckoutSession(any()))
            .thenThrow(mock(StripeException.class));

        assertThatThrownBy(() -> paymentService.initiateCheckout(userId))
            .isInstanceOf(PaymentException.class);

        verify(orderService).cancelOrder(order.getId());
    }

    @Test
    void initiateCheckout_lineItemUsesCorrectUnitAmount() throws StripeException {
        UUID userId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();
        Cart cart = stubCart(UUID.randomUUID(), userId);
        CartItem item = stubCartItem(variantId); // unitPrice = 49.99
        Order order = stubOrder(UUID.randomUUID(), userId);

        ProductVariant variant = new ProductVariant();
        variant.setTitle("Widget");
        variant.setPrice(new BigDecimal("49.99"));

        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test_456");
        when(session.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_456");

        when(cartService.requireActiveCart(userId)).thenReturn(cart);
        when(cartService.getCartItems(any())).thenReturn(List.of(item));
        when(orderService.createFromCart(eq(userId), any())).thenReturn(order);
        when(variantRepo.findById(variantId)).thenReturn(Optional.of(variant));
        when(stripeGateway.createCheckoutSession(any())).thenReturn(session);

        paymentService.initiateCheckout(userId);

        ArgumentCaptor<SessionCreateParams> captor = ArgumentCaptor.forClass(SessionCreateParams.class);
        verify(stripeGateway).createCheckoutSession(captor.capture());
        SessionCreateParams params = captor.getValue();
        SessionCreateParams.LineItem lineItem = params.getLineItems().get(0);
        assertThat(lineItem.getPriceData().getUnitAmount()).isEqualTo(4999L); // 49.99 * 100
    }
}
