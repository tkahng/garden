package io.k2dv.garden.payment.service;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
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
import io.k2dv.garden.quote.model.QuoteRequest;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.quote.repository.QuoteRequestRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
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

  @Mock
  CartService cartService;
  @Mock
  OrderService orderService;
  @Mock
  StripeGateway stripeGateway;
  @Mock
  ProductVariantRepository variantRepo;
  @Mock
  AppProperties appProperties;
  @Mock
  QuoteRequestRepository quoteRequestRepo;

  PaymentService paymentService;

  @BeforeEach
  void setUp() {
    Mockito.lenient().when(appProperties.getFrontendUrl()).thenReturn("http://localhost:3000");
    paymentService = new PaymentService(cartService, orderService, stripeGateway, variantRepo, appProperties, quoteRequestRepo);
  }

  private Cart stubCart(UUID userId) {
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
    Cart cart = stubCart(userId);
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
    Cart cart = stubCart(userId);
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
    Cart cart = stubCart(userId);
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

  @Test
  void initiateCheckout_emptyCart_throwsValidation() {
    UUID userId = UUID.randomUUID();
    Cart cart = stubCart(userId);

    when(cartService.requireActiveCart(userId)).thenReturn(cart);
    when(cartService.getCartItems(any())).thenReturn(List.of());

    assertThatThrownBy(() -> paymentService.initiateCheckout(userId))
        .isInstanceOf(ValidationException.class)
        .extracting("errorCode")
        .isEqualTo("EMPTY_CART");
  }

  @Test
  void verifyReturn_wrongUser_throwsValidation() {
    UUID ownerUserId = UUID.randomUUID();
    UUID callerUserId = UUID.randomUUID();

    Order order = new Order();
    order.setUserId(ownerUserId);
    order.setStatus(OrderStatus.PENDING_PAYMENT);

    when(orderService.findByStripeSessionId("cs_test_abc")).thenReturn(order);

    assertThatThrownBy(() -> paymentService.verifyReturn("cs_test_abc", callerUserId))
        .isInstanceOf(ValidationException.class)
        .extracting("errorCode")
        .isEqualTo("ORDER_NOT_OWNED");
  }

  @Test
  void verifyReturn_sessionNotFound_throwsNotFound() {
    when(orderService.findByStripeSessionId(any()))
        .thenThrow(new NotFoundException("ORDER_NOT_FOUND", "Not found"));

    assertThatThrownBy(() -> paymentService.verifyReturn("cs_test_missing", UUID.randomUUID()))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void verifyReturn_stripeComplete_returnsPaidStatus() throws StripeException {
    UUID userId = UUID.randomUUID();
    UUID orderId = UUID.randomUUID();
    Order order = new Order();
    order.setUserId(userId);
    order.setStatus(OrderStatus.PENDING_PAYMENT); // DB hasn't been updated yet

    Session session = mock(Session.class);
    when(session.getStatus()).thenReturn("complete");

    when(orderService.findByStripeSessionId("cs_test_abc")).thenReturn(order);
    when(stripeGateway.retrieveSession("cs_test_abc")).thenReturn(session);

    CheckoutReturnResponse response = paymentService.verifyReturn("cs_test_abc", userId);

    assertThat(response.status()).isEqualTo(OrderStatus.PAID);
  }

  @Test
  void verifyReturn_stripeCallFails_fallsBackToDbStatus() throws StripeException {
    UUID userId = UUID.randomUUID();
    Order order = new Order();
    order.setUserId(userId);
    order.setStatus(OrderStatus.PENDING_PAYMENT);

    when(orderService.findByStripeSessionId("cs_test_abc")).thenReturn(order);
    when(stripeGateway.retrieveSession("cs_test_abc")).thenThrow(mock(StripeException.class));

    CheckoutReturnResponse response = paymentService.verifyReturn("cs_test_abc", userId);

    assertThat(response.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
  }

  @Test
  void handleWebhook_sessionCompleted_callsConfirmPayment() throws SignatureVerificationException {
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("checkout.session.completed");

    EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
    Session session = mock(Session.class);
    when(session.getId()).thenReturn("cs_test_123");
    when(session.getPaymentIntent()).thenReturn("pi_test_456");
    when(session.getMetadata()).thenReturn(java.util.Map.of());
    when(deserializer.getObject()).thenReturn(Optional.of(session));
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);
    when(stripeGateway.constructEvent(any(), any(), any())).thenReturn(event);

    paymentService.handleWebhook("payload", "sig", "secret");

    verify(orderService).confirmPayment("cs_test_123", "pi_test_456");
  }

  @Test
  void handleWebhook_sessionCompleted_withQuoteOrderId_transitionsQuoteToPaid() throws SignatureVerificationException {
    UUID orderId = UUID.randomUUID();
    QuoteRequest quote = new QuoteRequest();
    quote.setStatus(QuoteStatus.ACCEPTED);

    Event event = mock(Event.class);
    when(event.getType()).thenReturn("checkout.session.completed");

    EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
    Session session = mock(Session.class);
    when(session.getId()).thenReturn("cs_test_123");
    when(session.getPaymentIntent()).thenReturn("pi_test_456");
    when(session.getMetadata()).thenReturn(java.util.Map.of("orderId", orderId.toString()));
    when(deserializer.getObject()).thenReturn(Optional.of(session));
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);
    when(stripeGateway.constructEvent(any(), any(), any())).thenReturn(event);
    when(quoteRequestRepo.findByOrderId(orderId)).thenReturn(Optional.of(quote));

    paymentService.handleWebhook("payload", "sig", "secret");

    verify(quoteRequestRepo).save(quote);
    assertThat(quote.getStatus()).isEqualTo(QuoteStatus.PAID);
  }

  @Test
  void handleWebhook_sessionCompleted_noOrderIdInMetadata_doesNotTouchQuoteRepo() throws SignatureVerificationException {
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("checkout.session.completed");

    EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
    Session session = mock(Session.class);
    when(session.getId()).thenReturn("cs_test_123");
    when(session.getPaymentIntent()).thenReturn("pi_test_456");
    when(session.getMetadata()).thenReturn(java.util.Map.of());
    when(deserializer.getObject()).thenReturn(Optional.of(session));
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);
    when(stripeGateway.constructEvent(any(), any(), any())).thenReturn(event);

    paymentService.handleWebhook("payload", "sig", "secret");

    verifyNoInteractions(quoteRequestRepo);
  }

  @Test
  void handleWebhook_sessionExpired_callsCancelBySession() throws SignatureVerificationException {
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("checkout.session.expired");

    EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
    Session session = mock(Session.class);
    when(session.getId()).thenReturn("cs_test_123");
    when(deserializer.getObject()).thenReturn(Optional.of(session));
    when(event.getDataObjectDeserializer()).thenReturn(deserializer);
    when(stripeGateway.constructEvent(any(), any(), any())).thenReturn(event);

    paymentService.handleWebhook("payload", "sig", "secret");

    verify(orderService).cancelBySession("cs_test_123");
  }

  @Test
  void handleWebhook_unknownEventType_doesNothing() throws SignatureVerificationException {
    Event event = mock(Event.class);
    when(event.getType()).thenReturn("payment_intent.created");
    when(stripeGateway.constructEvent(any(), any(), any())).thenReturn(event);

    paymentService.handleWebhook("payload", "sig", "secret");

    verifyNoInteractions(orderService);
  }

  @Test
  void handleWebhook_invalidSignature_throwsValidation() throws SignatureVerificationException {
    when(stripeGateway.constructEvent(any(), any(), any()))
        .thenThrow(mock(SignatureVerificationException.class));

    assertThatThrownBy(() -> paymentService.handleWebhook("payload", "bad-sig", "secret"))
        .isInstanceOf(ValidationException.class)
        .extracting("errorCode")
        .isEqualTo("INVALID_WEBHOOK_SIGNATURE");
  }
}
