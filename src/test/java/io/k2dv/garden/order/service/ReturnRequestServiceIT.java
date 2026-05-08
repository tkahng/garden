package io.k2dv.garden.order.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.cart.model.CartItem;
import io.k2dv.garden.inventory.model.InventoryLevel;
import io.k2dv.garden.inventory.model.Location;
import io.k2dv.garden.inventory.repository.InventoryItemRepository;
import io.k2dv.garden.inventory.repository.InventoryLevelRepository;
import io.k2dv.garden.inventory.repository.LocationRepository;
import io.k2dv.garden.order.dto.*;
import io.k2dv.garden.order.model.*;
import io.k2dv.garden.order.repository.OrderItemRepository;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.payment.gateway.StripeGateway;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

class ReturnRequestServiceIT extends AbstractIntegrationTest {

    @Autowired ReturnRequestService returnService;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orderRepo;
    @Autowired ProductService productService;
    @Autowired VariantService variantService;
    @Autowired LocationRepository locationRepo;
    @Autowired InventoryItemRepository inventoryItemRepo;
    @Autowired InventoryLevelRepository levelRepo;
    @Autowired OrderItemRepository orderItemRepo;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;
    @MockitoBean StripeGateway stripeGateway;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private UUID variantId;
    private UUID staffId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "ret-" + n + "-" + UUID.randomUUID() + "@example.com";
        String staffEmail = "ret-staff-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        authService.register(new RegisterRequest(staffEmail, "password1", "Staff", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();
        staffId = userRepo.findByEmail(staffEmail).orElseThrow().getId();

        AdminProductResponse product = productService.create(
            new CreateProductRequest("Widget", null, null, null, null, List.of(), null, null));
        productService.changeStatus(product.id(),
            new io.k2dv.garden.product.dto.ProductStatusRequest(ProductStatus.ACTIVE));
        AdminVariantResponse variant = variantService.create(product.id(),
            new CreateVariantRequest(new BigDecimal("50.00"), null, null, null, null, null, List.of()));
        variantId = variant.id();

        Location location = new Location();
        location.setName("Warehouse-" + n);
        location = locationRepo.save(location);

        InventoryLevel level = new InventoryLevel();
        level.setInventoryItem(inventoryItemRepo.findByVariantId(variantId).orElseThrow());
        level.setLocation(location);
        level.setQuantityOnHand(100);
        levelRepo.save(level);
    }

    private Order createPaidOrder() {
        CartItem item = new CartItem();
        item.setVariantId(variantId);
        item.setQuantity(2);
        item.setUnitPrice(new BigDecimal("50.00"));
        Order order = orderService.createFromCart(userId, List.of(item));
        // Directly mark as PAID to bypass Stripe in integration tests
        order.setStatus(OrderStatus.PAID);
        order = orderRepo.save(order);
        return order;
    }

    private SubmitReturnRequest submitReq(ReturnReason reason, ReturnResolution resolution) {
        return new SubmitReturnRequest(reason, "Please refund", resolution, null);
    }

    @Test
    void submit_paidOrder_createsPendingReturn() {
        Order order = createPaidOrder();

        ReturnRequestResponse rr = returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.DAMAGED, ReturnResolution.REFUND));

        assertThat(rr.status()).isEqualTo(ReturnRequestStatus.PENDING);
        assertThat(rr.reason()).isEqualTo(ReturnReason.DAMAGED);
        assertThat(rr.resolution()).isEqualTo(ReturnResolution.REFUND);
        assertThat(rr.orderId()).isEqualTo(order.getId());
    }

    @Test
    void submit_withLineItems_savesItems() {
        Order order = createPaidOrder();
        UUID orderItemId = orderItemRepo.findByOrderId(order.getId()).get(0).getId();

        SubmitReturnRequest req = new SubmitReturnRequest(ReturnReason.WRONG_ITEM, null,
            ReturnResolution.EXCHANGE, List.of(new ReturnRequestItemInput(orderItemId, 1)));

        ReturnRequestResponse rr = returnService.submit(order.getId(), userId, req);

        assertThat(rr.items()).hasSize(1);
        assertThat(rr.items().get(0).orderItemId()).isEqualTo(orderItemId);
        assertThat(rr.items().get(0).quantity()).isEqualTo(1);
    }

    @Test
    void submit_pendingPaymentOrder_throws409() {
        CartItem item = new CartItem();
        item.setVariantId(variantId);
        item.setQuantity(1);
        item.setUnitPrice(new BigDecimal("50.00"));
        Order order = orderService.createFromCart(userId, List.of(item));

        assertThatThrownBy(() -> returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.CHANGED_MIND, ReturnResolution.REFUND)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("paid or fulfilled");
    }

    @Test
    void submit_wrongUser_throwsValidation() {
        Order order = createPaidOrder();
        UUID otherId = UUID.randomUUID();

        assertThatThrownBy(() -> returnService.submit(order.getId(), otherId,
            submitReq(ReturnReason.DAMAGED, ReturnResolution.REFUND)))
            .isInstanceOf(ValidationException.class);
    }

    @Test
    void submit_duplicateOpenReturn_throws409() {
        Order order = createPaidOrder();
        returnService.submit(order.getId(), userId, submitReq(ReturnReason.DAMAGED, ReturnResolution.REFUND));

        assertThatThrownBy(() -> returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.DAMAGED, ReturnResolution.REFUND)))
            .isInstanceOf(ConflictException.class)
            .hasMessageContaining("already exists");
    }

    @Test
    void approve_withRefundResolution_changesStatusAndReturnsApproved() throws Exception {
        Order order = createPaidOrder();
        ReturnRequestResponse rr = returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.DAMAGED, ReturnResolution.REFUND));

        doReturn(null).when(stripeGateway).createRefund(any());

        ReturnRequestResponse approved = returnService.approve(rr.id(), staffId,
            new ReviewReturnRequest("Looks good"));

        assertThat(approved.status()).isEqualTo(ReturnRequestStatus.APPROVED);
        assertThat(approved.staffNotes()).isEqualTo("Looks good");
        assertThat(approved.resolvedBy()).isEqualTo(staffId);
    }

    @Test
    void approve_exchangeResolution_skipsStripeRefund() {
        Order order = createPaidOrder();
        ReturnRequestResponse rr = returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.WRONG_ITEM, ReturnResolution.EXCHANGE));

        ReturnRequestResponse approved = returnService.approve(rr.id(), staffId, null);

        assertThat(approved.status()).isEqualTo(ReturnRequestStatus.APPROVED);
    }

    @Test
    void approve_alreadyApproved_throws409() throws Exception {
        Order order = createPaidOrder();
        ReturnRequestResponse rr = returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.DAMAGED, ReturnResolution.EXCHANGE));
        returnService.approve(rr.id(), staffId, null);

        assertThatThrownBy(() -> returnService.approve(rr.id(), staffId, null))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void reject_pendingReturn_changesStatus() {
        Order order = createPaidOrder();
        ReturnRequestResponse rr = returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.CHANGED_MIND, ReturnResolution.REFUND));

        ReturnRequestResponse rejected = returnService.reject(rr.id(), staffId,
            new ReviewReturnRequest("Policy does not allow this"));

        assertThat(rejected.status()).isEqualTo(ReturnRequestStatus.REJECTED);
        assertThat(rejected.staffNotes()).contains("Policy");
    }

    @Test
    void complete_approvedReturn_changesStatus() {
        Order order = createPaidOrder();
        ReturnRequestResponse rr = returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.DAMAGED, ReturnResolution.EXCHANGE));
        returnService.approve(rr.id(), staffId, null);

        ReturnRequestResponse completed = returnService.complete(rr.id(), staffId);

        assertThat(completed.status()).isEqualTo(ReturnRequestStatus.COMPLETED);
    }

    @Test
    void complete_pendingReturn_throws409() {
        Order order = createPaidOrder();
        ReturnRequestResponse rr = returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.DAMAGED, ReturnResolution.EXCHANGE));

        assertThatThrownBy(() -> returnService.complete(rr.id(), staffId))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void listForUser_returnsOwnReturns() {
        Order order = createPaidOrder();
        returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.DAMAGED, ReturnResolution.REFUND));

        var page = returnService.listForUser(userId, PageRequest.of(0, 20));
        assertThat(page.getContent()).hasSize(1);
    }

    @Test
    void getByIdForUser_wrongUser_throws404() {
        Order order = createPaidOrder();
        ReturnRequestResponse rr = returnService.submit(order.getId(), userId,
            submitReq(ReturnReason.DAMAGED, ReturnResolution.REFUND));

        assertThatThrownBy(() -> returnService.getByIdForUser(rr.id(), UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }
}
