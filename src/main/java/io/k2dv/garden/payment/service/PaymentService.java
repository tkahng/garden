package io.k2dv.garden.payment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.param.checkout.SessionCreateParams;
import io.k2dv.garden.cart.model.Cart;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.discount.dto.DiscountApplication;
import io.k2dv.garden.discount.service.DiscountService;
import io.k2dv.garden.giftcard.dto.GiftCardApplication;
import io.k2dv.garden.giftcard.service.GiftCardService;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderEventType;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.service.OrderEventService;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.payment.dto.CheckoutResponse;
import io.k2dv.garden.payment.dto.CheckoutReturnResponse;
import io.k2dv.garden.payment.dto.GuestAddressRequest;
import io.k2dv.garden.payment.exception.PaymentException;
import io.k2dv.garden.payment.gateway.StripeGateway;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.quote.model.QuoteItem;
import io.k2dv.garden.quote.model.QuoteRequest;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.quote.repository.QuoteRequestRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.shipping.model.ShippingRate;
import io.k2dv.garden.shipping.repository.ShippingRateRepository;
import io.k2dv.garden.user.model.Address;
import io.k2dv.garden.user.repository.AddressRepository;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PaymentService {

  private final CartService cartService;
  private final OrderService orderService;
  private final StripeGateway stripeGateway;
  private final ProductVariantRepository variantRepo;
  private final AppProperties appProperties;
  private final QuoteRequestRepository quoteRequestRepo;
  private final AddressRepository addressRepo;
  private final DiscountService discountService;
  private final GiftCardService giftCardService;
  private final OrderEventService orderEventService;
  private final ShippingRateRepository shippingRateRepo;
  private final UserRepository userRepo;

  private static final ObjectMapper MAPPER = new ObjectMapper();

  // NOT @Transactional — Stripe call is outside transaction; each sub-call manages its own tx
  public CheckoutResponse initiateCheckout(UUID userId, String discountCode, String giftCardCode) {
    return initiateCheckout(userId, discountCode, giftCardCode, null);
  }

  public CheckoutResponse initiateCheckout(UUID userId, String discountCode, String giftCardCode,
                                           UUID shippingRateId) {
    Address defaultAddress = addressRepo.findByUserIdAndIsDefaultTrue(userId)
        .orElseThrow(() -> new ValidationException("NO_SHIPPING_ADDRESS",
            "A default shipping address is required before checkout"));

    Cart cart = cartService.requireActiveCart(userId);
    List<CartItem> cartItems = cartService.getCartItems(cart.getId());

    if (cartItems.isEmpty()) {
      throw new ValidationException("EMPTY_CART", "Cart has no items");
    }

    ShippingRate shippingRate = resolveShippingRate(shippingRateId);
    BigDecimal shippingCost = shippingRate != null ? shippingRate.getPrice() : null;
    String shippingAddressJson = serializeAddress(defaultAddress);

    Order order = orderService.createFromCart(userId, cartItems, shippingRateId, shippingCost, shippingAddressJson);

    order = applyDiscountAndGiftCard(order, discountCode, giftCardCode);

    if (order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
      cartService.markCheckedOut(cart.getId());
      return new CheckoutResponse(null, order.getId());
    }

    try {
      SessionCreateParams.Builder builder = buildSessionBase(order.getCurrency(), order.getId())
          .setCustomerEmail(userRepo.findById(userId).map(u -> u.getEmail()).orElse(null));

      addLineItems(builder, cartItems, order, shippingRate, discountCode != null && !discountCode.isBlank());

      Session session = stripeGateway.createCheckoutSession(builder.build());
      orderService.setStripeSession(order.getId(), session.getId());
      cartService.markCheckedOut(cart.getId());

      return new CheckoutResponse(session.getUrl(), order.getId());

    } catch (StripeException e) {
      orderService.cancelOrder(order.getId());
      throw new PaymentException("STRIPE_ERROR", "Failed to create checkout session: " + e.getMessage());
    }
  }

  public CheckoutResponse initiateGuestCheckout(String guestEmail, GuestAddressRequest guestAddress,
                                                 UUID shippingRateId, String discountCode,
                                                 String giftCardCode, UUID sessionId) {
    if (userRepo.existsByEmail(guestEmail)) {
      throw new ValidationException("EMAIL_EXISTS",
          "An account with this email already exists. Please log in to continue.");
    }

    ShippingRate shippingRate = shippingRateRepo.findById(shippingRateId)
        .orElseThrow(() -> new ValidationException("SHIPPING_RATE_NOT_FOUND", "Invalid shipping rate selected"));

    Cart cart = cartService.requireActiveGuestCart(sessionId);
    List<CartItem> cartItems = cartService.getCartItems(cart.getId());

    if (cartItems.isEmpty()) {
      throw new ValidationException("EMPTY_CART", "Cart has no items");
    }

    BigDecimal shippingCost = shippingRate.getPrice();
    String shippingAddressJson = serializeGuestAddress(guestAddress);

    Order order = orderService.createGuestOrder(guestEmail, cartItems, shippingRateId, shippingCost, shippingAddressJson);

    order = applyDiscountAndGiftCard(order, discountCode, giftCardCode);

    if (order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
      cartService.markCheckedOut(cart.getId());
      return new CheckoutResponse(null, order.getId());
    }

    try {
      SessionCreateParams.Builder builder = buildSessionBase(order.getCurrency(), order.getId())
          .setCustomerEmail(guestEmail);

      addLineItems(builder, cartItems, order, shippingRate, discountCode != null && !discountCode.isBlank());

      Session session = stripeGateway.createCheckoutSession(builder.build());
      orderService.setStripeSession(order.getId(), session.getId());
      cartService.markCheckedOut(cart.getId());

      return new CheckoutResponse(session.getUrl(), order.getId());

    } catch (StripeException e) {
      orderService.cancelOrder(order.getId());
      throw new PaymentException("STRIPE_ERROR", "Failed to create checkout session: " + e.getMessage());
    }
  }

  // NOT @Transactional — Stripe call is outside transaction
  public CheckoutResponse createCheckoutSessionFromQuote(Order order, List<QuoteItem> items, QuoteRequest quote) {
    try {
      String currency = order.getCurrency() != null ? order.getCurrency() : "usd";

      SessionCreateParams.Builder builder = SessionCreateParams.builder()
          .setMode(SessionCreateParams.Mode.PAYMENT)
          .setSuccessUrl(appProperties.getFrontendUrl()
              + "/checkout/return?session_id={CHECKOUT_SESSION_ID}")
          .setCancelUrl(appProperties.getFrontendUrl()
              + "/checkout/return?session_id={CHECKOUT_SESSION_ID}")
          .putMetadata("orderId", order.getId() != null ? order.getId().toString() : "")
          .setAutomaticTax(SessionCreateParams.AutomaticTax.builder()
              .setEnabled(true)
              .build())
          .setBillingAddressCollection(SessionCreateParams.BillingAddressCollection.REQUIRED);

      for (QuoteItem item : items) {
        if (item.getUnitPrice() == null) continue;
        long unitAmountCents = item.getUnitPrice()
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();

        builder.addLineItem(
            SessionCreateParams.LineItem.builder()
                .setQuantity((long) item.getQuantity())
                .setPriceData(
                    SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency)
                        .setUnitAmount(unitAmountCents)
                        .setProductData(
                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName(item.getDescription())
                                .build())
                        .build())
                .build());
      }

      Session session = stripeGateway.createCheckoutSession(builder.build());
      orderService.setStripeSession(order.getId(), session.getId());

      return new CheckoutResponse(session.getUrl(), order.getId());

    } catch (StripeException e) {
      orderService.cancelOrder(order.getId());
      throw new PaymentException("STRIPE_ERROR",
          "Failed to create checkout session: " + e.getMessage());
    }
  }

  public CheckoutReturnResponse verifyReturn(String stripeSessionId, UUID userId) {
    Order order = orderService.findByStripeSessionId(stripeSessionId);

    // Only enforce ownership for authenticated users on authenticated orders
    if (userId != null && order.getUserId() != null && !order.getUserId().equals(userId)) {
      throw new ValidationException("ORDER_NOT_OWNED", "Order does not belong to current user");
    }

    OrderStatus status = order.getStatus();
    try {
      Session session = stripeGateway.retrieveSession(stripeSessionId);
      status = switch (session.getStatus()) {
        case "complete" -> OrderStatus.PAID;
        case "expired" -> OrderStatus.CANCELLED;
        default -> order.getStatus();
      };
    } catch (StripeException e) {
      // best-effort — use DB status as fallback
    }

    return new CheckoutReturnResponse(order.getId(), status);
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
        Session session = deserializeSession(event);
        Long taxAmountCents = session.getTotalDetails() != null
            ? session.getTotalDetails().getAmountTax() : null;
        orderService.confirmPayment(session.getId(), session.getPaymentIntent(), taxAmountCents);
        String orderIdStr = session.getMetadata() != null ? session.getMetadata().get("orderId") : null;
        if (orderIdStr != null && !orderIdStr.isBlank()) {
          quoteRequestRepo.findByOrderId(UUID.fromString(orderIdStr)).ifPresent(quote -> {
            if (quote.getStatus() == QuoteStatus.ACCEPTED) {
              quote.setStatus(QuoteStatus.PAID);
              quoteRequestRepo.save(quote);
            }
          });
        }
      }
      case "checkout.session.expired" -> {
        Session session = deserializeSession(event);
        orderService.cancelBySession(session.getId());
      }
      default -> { /* ignore */ }
    }
  }

  // --- Helpers ---

  private SessionCreateParams.Builder buildSessionBase(String currency, UUID orderId) {
    String cur = currency != null ? currency : "usd";
    return SessionCreateParams.builder()
        .setMode(SessionCreateParams.Mode.PAYMENT)
        .setSuccessUrl(appProperties.getFrontendUrl() + "/checkout/return?session_id={CHECKOUT_SESSION_ID}")
        .setCancelUrl(appProperties.getFrontendUrl() + "/checkout/return?session_id={CHECKOUT_SESSION_ID}")
        .putMetadata("orderId", orderId != null ? orderId.toString() : "")
        .setAutomaticTax(SessionCreateParams.AutomaticTax.builder().setEnabled(true).build());
  }

  private void addLineItems(SessionCreateParams.Builder builder, List<CartItem> cartItems,
                            Order order, ShippingRate shippingRate, boolean discountApplied) {
    String currency = order.getCurrency() != null ? order.getCurrency() : "usd";

    if (discountApplied) {
      // Single line item = full discounted+shipping total
      long totalCents = order.getTotalAmount()
          .multiply(BigDecimal.valueOf(100))
          .setScale(0, RoundingMode.HALF_UP)
          .longValueExact();
      builder.addLineItem(
          SessionCreateParams.LineItem.builder()
              .setQuantity(1L)
              .setPriceData(
                  SessionCreateParams.LineItem.PriceData.builder()
                      .setCurrency(currency)
                      .setUnitAmount(totalCents)
                      .setProductData(
                          SessionCreateParams.LineItem.PriceData.ProductData.builder()
                              .setName("Order Total (discount applied)")
                              .build())
                      .build())
              .build());
    } else {
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
                                .build())
                        .build())
                .build());
      }

      if (shippingRate != null && shippingRate.getPrice().compareTo(BigDecimal.ZERO) > 0) {
        long shippingCents = shippingRate.getPrice()
            .multiply(BigDecimal.valueOf(100))
            .setScale(0, RoundingMode.HALF_UP)
            .longValueExact();
        builder.addLineItem(
            SessionCreateParams.LineItem.builder()
                .setQuantity(1L)
                .setPriceData(
                    SessionCreateParams.LineItem.PriceData.builder()
                        .setCurrency(currency)
                        .setUnitAmount(shippingCents)
                        .setProductData(
                            SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                .setName("Shipping — " + shippingRate.getName())
                                .build())
                        .build())
                .build());
      }
    }
  }

  private Order applyDiscountAndGiftCard(Order order, String discountCode, String giftCardCode) {
    if (discountCode != null && !discountCode.isBlank()) {
      DiscountApplication discount = discountService.redeem(discountCode, order.getTotalAmount());
      orderService.applyDiscount(order.getId(), discount.discountId(), discount.discountedAmount());
      order = orderService.getById(order.getId());
      orderEventService.emit(order.getId(), OrderEventType.DISCOUNT_APPLIED,
          "Discount '" + discount.code() + "' applied (−" + discount.discountedAmount() + ")",
          null, "system", Map.of("discountId", discount.discountId().toString(),
              "amount", discount.discountedAmount().toPlainString()));
    }

    if (giftCardCode != null && !giftCardCode.isBlank()) {
      GiftCardApplication gca = giftCardService.redeem(giftCardCode, order.getTotalAmount(),
          order.getId(), order.getCurrency());
      orderService.applyGiftCard(order.getId(), gca.giftCardId(), gca.appliedAmount());
      order = orderService.getById(order.getId());
      orderEventService.emit(order.getId(), OrderEventType.GIFT_CARD_APPLIED,
          "Gift card '" + gca.code() + "' applied (−" + gca.appliedAmount() + ")",
          null, "system", Map.of("giftCardId", gca.giftCardId().toString(),
              "amount", gca.appliedAmount().toPlainString()));

      if (order.getTotalAmount().compareTo(BigDecimal.ZERO) <= 0) {
        orderService.markPaidDirectly(order.getId());
      }
    }

    return order;
  }

  private ShippingRate resolveShippingRate(UUID shippingRateId) {
    if (shippingRateId == null) return null;
    return shippingRateRepo.findById(shippingRateId)
        .orElseThrow(() -> new ValidationException("SHIPPING_RATE_NOT_FOUND", "Invalid shipping rate selected"));
  }

  private String serializeAddress(Address address) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("firstName", address.getFirstName());
    map.put("lastName", address.getLastName());
    map.put("company", address.getCompany());
    map.put("address1", address.getAddress1());
    map.put("address2", address.getAddress2());
    map.put("city", address.getCity());
    map.put("province", address.getProvince());
    map.put("zip", address.getZip());
    map.put("country", address.getCountry());
    return toJson(map);
  }

  private String serializeGuestAddress(GuestAddressRequest req) {
    Map<String, Object> map = new LinkedHashMap<>();
    map.put("firstName", req.firstName());
    map.put("lastName", req.lastName());
    map.put("address1", req.address1());
    map.put("address2", req.address2());
    map.put("city", req.city());
    map.put("province", req.province());
    map.put("zip", req.zip());
    map.put("country", req.country());
    return toJson(map);
  }

  private String toJson(Object value) {
    try {
      return MAPPER.writeValueAsString(value);
    } catch (JsonProcessingException e) {
      throw new PaymentException("SERIALIZATION_ERROR", "Failed to serialize address");
    }
  }

  private Session deserializeSession(Event event) {
    var deserializer = event.getDataObjectDeserializer();
    if (deserializer.getObject().isPresent()) {
      return (Session) deserializer.getObject().get();
    }
    try {
      return (Session) deserializer.deserializeUnsafe();
    } catch (Exception e) {
      throw new PaymentException("STRIPE_DESERIALIZE_ERROR", "Cannot deserialize Stripe session");
    }
  }
}
