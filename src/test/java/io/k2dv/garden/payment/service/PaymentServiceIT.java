package io.k2dv.garden.payment.service;

import com.stripe.exception.StripeException;
import com.stripe.model.checkout.Session;
import io.k2dv.garden.account.dto.AddressRequest;
import io.k2dv.garden.account.service.AccountService;
import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.cart.dto.AddCartItemRequest;
import io.k2dv.garden.cart.service.CartService;
import io.k2dv.garden.inventory.model.InventoryLevel;
import io.k2dv.garden.inventory.model.Location;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.payment.dto.CheckoutResponse;
import io.k2dv.garden.payment.exception.PaymentException;
import io.k2dv.garden.payment.gateway.StripeGateway;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.dto.ProductStatusRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentServiceIT extends AbstractIntegrationTest {

    @Autowired PaymentService paymentService;
    @Autowired AuthService authService;
    @Autowired AccountService accountService;
    @Autowired CartService cartService;
    @Autowired ProductService productService;
    @Autowired VariantService variantService;
    @Autowired UserRepository userRepo;
    @Autowired LocationRepository locationRepo;
    @Autowired InventoryItemRepository inventoryItemRepo;
    @Autowired InventoryLevelRepository levelRepo;

    @MockitoBean StripeGateway stripeGateway;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private AdminVariantResponse variant;
    private UUID userId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "payment-it-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();

        AdminProductResponse product = productService.create(
            new CreateProductRequest("Test Plant", null, null, null, null, List.of()));
        productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
        variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("25.00"), null, null, null, null, null, List.of()));

        Location location = new Location();
        location.setName("Warehouse");
        location = locationRepo.save(location);

        InventoryLevel level = new InventoryLevel();
        level.setInventoryItem(inventoryItemRepo.findByVariantId(variant.id()).orElseThrow());
        level.setLocation(location);
        level.setQuantityOnHand(10);
        levelRepo.save(level);

        cartService.getOrCreateActiveCart(userId);
    }

    private void addDefaultAddress() {
        accountService.createAddress(userId, new AddressRequest(
            "Test", "User", null, "1 Main St", null, "Portland", "OR", "97201", "US", true));
    }

    @Test
    void initiateCheckout_noDefaultAddress_throwsValidation() {
        cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1));

        assertThatThrownBy(() -> paymentService.initiateCheckout(userId, null, null))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode")
            .isEqualTo("NO_SHIPPING_ADDRESS");
    }

    @Test
    void initiateCheckout_emptyCart_throwsValidation() {
        addDefaultAddress();

        assertThatThrownBy(() -> paymentService.initiateCheckout(userId, null, null))
            .isInstanceOf(ValidationException.class)
            .extracting("errorCode")
            .isEqualTo("EMPTY_CART");
    }

    @Test
    void initiateCheckout_happyPath_returnsCheckoutUrl() throws StripeException {
        addDefaultAddress();
        cartService.addItem(userId, new AddCartItemRequest(variant.id(), 2));

        Session session = mock(Session.class);
        when(session.getId()).thenReturn("cs_test_it_123");
        when(session.getUrl()).thenReturn("https://checkout.stripe.com/pay/cs_test_it_123");
        when(stripeGateway.createCheckoutSession(any())).thenReturn(session);

        CheckoutResponse response = paymentService.initiateCheckout(userId, null, null);

        assertThat(response.checkoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_it_123");
        assertThat(response.orderId()).isNotNull();
    }

    @Test
    void initiateCheckout_stripeFailure_cancelsOrder() throws StripeException {
        addDefaultAddress();
        cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1));

        when(stripeGateway.createCheckoutSession(any())).thenThrow(mock(StripeException.class));

        assertThatThrownBy(() -> paymentService.initiateCheckout(userId, null, null))
            .isInstanceOf(PaymentException.class)
            .extracting("errorCode")
            .isEqualTo("STRIPE_ERROR");
    }
}
