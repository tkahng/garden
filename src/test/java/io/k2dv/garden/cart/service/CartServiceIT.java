package io.k2dv.garden.cart.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.cart.dto.AddCartItemRequest;
import io.k2dv.garden.cart.dto.BulkAddToCartResponse;
import io.k2dv.garden.cart.dto.CartResponse;
import io.k2dv.garden.cart.dto.UpdateCartItemRequest;
import io.k2dv.garden.cart.model.CartStatus;
import io.k2dv.garden.product.dto.CreateProductRequest;
import io.k2dv.garden.product.dto.CreateVariantRequest;
import io.k2dv.garden.product.dto.AdminProductResponse;
import io.k2dv.garden.product.dto.AdminVariantResponse;
import io.k2dv.garden.product.dto.ProductStatusRequest;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.ProductService;
import io.k2dv.garden.product.service.VariantService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CartServiceIT extends AbstractIntegrationTest {

  @Autowired
  CartService cartService;
  @Autowired
  ProductService productService;
  @Autowired
  VariantService variantService;
  @Autowired
  AuthService authService;
  @Autowired
  UserRepository userRepo;
  @MockitoBean
  EmailService emailService;

  private static final AtomicInteger counter = new AtomicInteger(0);

  private AdminVariantResponse createActiveVariant(BigDecimal price) {
    AdminProductResponse product = productService.create(
        new CreateProductRequest("Test Product", null, null, null, null, List.of(), null, null));
    productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
    return variantService.create(product.id(),
        new CreateVariantRequest(price, null, null, null, null, null, List.of()));
  }

  private UUID createUserId() {
    int n = counter.incrementAndGet();
    String email = "cart-test-" + n + "-" + UUID.randomUUID() + "@example.com";
    authService.register(new RegisterRequest(email, "password1", "Test", "User"));
    return userRepo.findByEmail(email).orElseThrow().getId();
  }

  @Test
  void getOrCreateActiveCart_createsNewCart() {
    UUID userId = createUserId();
    CartResponse cart = cartService.getOrCreateActiveCart(userId);
    assertThat(cart.status()).isEqualTo(CartStatus.ACTIVE);
    assertThat(cart.items()).isEmpty();
  }

  @Test
  void getOrCreateActiveCart_returnsExistingCart() {
    UUID userId = createUserId();
    CartResponse first = cartService.getOrCreateActiveCart(userId);
    CartResponse second = cartService.getOrCreateActiveCart(userId);
    assertThat(second.id()).isEqualTo(first.id());
  }

  @Test
  void addItem_snapshotsUnitPrice() {
    UUID userId = createUserId();
    AdminVariantResponse variant = createActiveVariant(new BigDecimal("49.99"));
    cartService.getOrCreateActiveCart(userId);

    CartResponse cart = cartService.addItem(userId, new AddCartItemRequest(variant.id(), 2));
    assertThat(cart.items()).hasSize(1);
    assertThat(cart.items().get(0).quantity()).isEqualTo(2);
    assertThat(cart.items().get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("49.99"));
  }

  @Test
  void addItem_productInfoPopulated() {
    UUID userId = createUserId();
    AdminProductResponse product = productService.create(
        new CreateProductRequest("Garden Hose", null, null, null, null, List.of(), null, null));
    productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
    AdminVariantResponse variant = variantService.create(product.id(),
        new CreateVariantRequest(new BigDecimal("19.99"), null, null, null, null, null, List.of()));
    cartService.getOrCreateActiveCart(userId);

    CartResponse cart = cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1));

    assertThat(cart.items()).hasSize(1);
    var productInfo = cart.items().get(0).product();
    assertThat(productInfo).isNotNull();
    assertThat(productInfo.productId()).isEqualTo(product.id());
    assertThat(productInfo.productTitle()).isEqualTo("Garden Hose");
    assertThat(productInfo.variantTitle()).isNotNull();
    assertThat(productInfo.imageUrl()).isNull(); // no image attached
  }

  @Test
  void addItem_sameVariantTwice_mergesQuantity() {
    UUID userId = createUserId();
    AdminVariantResponse variant = createActiveVariant(new BigDecimal("10.00"));
    cartService.getOrCreateActiveCart(userId);
    cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1));

    CartResponse cart = cartService.addItem(userId, new AddCartItemRequest(variant.id(), 3));
    assertThat(cart.items()).hasSize(1);
    assertThat(cart.items().get(0).quantity()).isEqualTo(4);
  }

  @Test
  void addItem_variantNotFound_throwsNotFound() {
    UUID userId = createUserId();
    cartService.getOrCreateActiveCart(userId);
    assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(UUID.randomUUID(), 1)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void updateItem_changesQuantity() {
    UUID userId = createUserId();
    AdminVariantResponse variant = createActiveVariant(new BigDecimal("20.00"));
    cartService.getOrCreateActiveCart(userId);
    CartResponse cart = cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1));
    UUID itemId = cart.items().get(0).id();

    CartResponse updated = cartService.updateItem(userId, itemId, new UpdateCartItemRequest(5));
    assertThat(updated.items().get(0).quantity()).isEqualTo(5);
  }

  @Test
  void removeItem_removesItem() {
    UUID userId = createUserId();
    AdminVariantResponse variant = createActiveVariant(new BigDecimal("5.00"));
    cartService.getOrCreateActiveCart(userId);
    CartResponse cart = cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1));
    UUID itemId = cart.items().get(0).id();

    CartResponse updated = cartService.removeItem(userId, itemId);
    assertThat(updated.items()).isEmpty();
  }

  @Test
  void addItem_inactiveProduct_throwsValidation() {
    UUID userId = createUserId();
    // Create a DRAFT product (not activated)
    AdminProductResponse draftProduct = productService.create(
        new CreateProductRequest("Draft Product", null, null, null, null, List.of(), null, null));
    AdminVariantResponse variant = variantService.create(draftProduct.id(),
        new CreateVariantRequest(new BigDecimal("10.00"), null, null, null, null, null, List.of()));
    cartService.getOrCreateActiveCart(userId);

    assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void abandonCart_transitionsStatus() {
    UUID userId = createUserId();
    cartService.getOrCreateActiveCart(userId);
    cartService.abandonCart(userId);

    // Abandoning again should not fail (no active cart to abandon — just a no-op)
    cartService.abandonCart(userId);
  }

  @Test
  void addItem_noActiveCart_throwsValidation() {
    UUID userId = createUserId();
    AdminVariantResponse variant = createActiveVariant(new BigDecimal("10.00"));
    // No getOrCreateActiveCart call — user has no cart
    assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1)))
        .isInstanceOf(ValidationException.class);
  }

  @Test
  void addItem_softDeletedVariant_throwsNotFound() {
    UUID userId = createUserId();
    // Create product + variant manually to retain the productId for softDelete
    AdminProductResponse product = productService.create(
        new CreateProductRequest("Soft-Delete Product", null, null, null, null, List.of(), null, null));
    productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
    AdminVariantResponse variant = variantService.create(product.id(),
        new CreateVariantRequest(new BigDecimal("15.00"), null, null, null, null, null, List.of()));
    // Soft-delete the variant
    variantService.softDelete(product.id(), variant.id());

    cartService.getOrCreateActiveCart(userId);
    assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1)))
        .isInstanceOf(NotFoundException.class);
  }

  @Test
  void addItem_checkedOutCart_throwsValidation() {
    UUID userId = createUserId();
    AdminVariantResponse variant = createActiveVariant(new BigDecimal("10.00"));
    CartResponse cart = cartService.getOrCreateActiveCart(userId);
    cartService.markCheckedOut(cart.id()); // transitions to CHECKED_OUT

    assertThatThrownBy(() -> cartService.addItem(userId, new AddCartItemRequest(variant.id(), 1)))
        .isInstanceOf(ValidationException.class);
  }

  private AdminVariantResponse createActiveVariantWithSku(BigDecimal price, String sku) {
    AdminProductResponse product = productService.create(
        new CreateProductRequest("CSV Import Product", null, null, null, null, List.of(), null, null));
    productService.changeStatus(product.id(), new ProductStatusRequest(ProductStatus.ACTIVE));
    return variantService.create(product.id(),
        new CreateVariantRequest(price, null, sku, null, null, null, List.of()));
  }

  @Test
  void addItemsFromCsv_addsKnownSkus_andMarksMissingAsNotFound() {
    UUID userId = createUserId();
    AdminVariantResponse variant = createActiveVariantWithSku(new BigDecimal("9.99"), "CSV-SKU-001");

    String csv = "sku,quantity\nCSV-SKU-001,3\nNONEXISTENT-SKU,2\n";
    MockMultipartFile file = new MockMultipartFile("file", "order.csv", "text/csv", csv.getBytes());

    BulkAddToCartResponse result = cartService.addItemsFromCsv(userId, file);

    assertThat(result.results()).hasSize(2);
    BulkAddToCartResponse.LineResult added = result.results().stream()
        .filter(r -> r.status() == BulkAddToCartResponse.Status.ADDED).findFirst().orElseThrow();
    assertThat(added.sku()).isEqualTo("CSV-SKU-001");
    assertThat(added.quantity()).isEqualTo(3);
    assertThat(added.variantId()).isEqualTo(variant.id());

    BulkAddToCartResponse.LineResult notFound = result.results().stream()
        .filter(r -> r.status() == BulkAddToCartResponse.Status.NOT_FOUND).findFirst().orElseThrow();
    assertThat(notFound.sku()).isEqualTo("NONEXISTENT-SKU");

    assertThat(result.cart().items()).anyMatch(i -> i.variantId().equals(variant.id()) && i.quantity() == 3);
  }

  @Test
  void addItemsFromCsv_skipsHeaderAndBlankLines() {
    UUID userId = createUserId();
    AdminVariantResponse variant = createActiveVariantWithSku(new BigDecimal("5.00"), "CSV-SKU-002");

    String csv = "sku,quantity\n\nCSV-SKU-002,1\n\n";
    MockMultipartFile file = new MockMultipartFile("file", "order.csv", "text/csv", csv.getBytes());

    BulkAddToCartResponse result = cartService.addItemsFromCsv(userId, file);

    assertThat(result.results()).hasSize(1);
    assertThat(result.results().get(0).status()).isEqualTo(BulkAddToCartResponse.Status.ADDED);
  }

  @Test
  void addItemsFromCsv_emptyFile_returnsEmptyResults() {
    UUID userId = createUserId();
    MockMultipartFile file = new MockMultipartFile("file", "empty.csv", "text/csv", "sku,quantity\n".getBytes());

    BulkAddToCartResponse result = cartService.addItemsFromCsv(userId, file);

    assertThat(result.results()).isEmpty();
  }

  // --- mergeGuestCartIntoUserCart ---

  @Test
  void mergeGuestCartIntoUserCart_transfersItemsAndAbandonsGuestCart() {
    UUID userId = createUserId();
    AdminVariantResponse variant = createActiveVariant(new BigDecimal("29.99"));
    UUID sessionId = UUID.randomUUID();

    cartService.getOrCreateGuestCart(sessionId);
    cartService.addGuestItem(sessionId, new AddCartItemRequest(variant.id(), 2));

    cartService.mergeGuestCartIntoUserCart(sessionId, userId);

    CartResponse userCart = cartService.getOrCreateActiveCart(userId);
    assertThat(userCart.items()).hasSize(1);
    assertThat(userCart.items().get(0).variantId()).isEqualTo(variant.id());
    assertThat(userCart.items().get(0).quantity()).isEqualTo(2);
    assertThat(userCart.items().get(0).unitPrice()).isEqualByComparingTo(new BigDecimal("29.99"));
  }

  @Test
  void mergeGuestCartIntoUserCart_deduplicatesByVariant() {
    UUID userId = createUserId();
    AdminVariantResponse variant = createActiveVariant(new BigDecimal("19.99"));
    UUID sessionId = UUID.randomUUID();

    cartService.getOrCreateActiveCart(userId);
    cartService.addItem(userId, new AddCartItemRequest(variant.id(), 3));
    cartService.getOrCreateGuestCart(sessionId);
    cartService.addGuestItem(sessionId, new AddCartItemRequest(variant.id(), 5));

    cartService.mergeGuestCartIntoUserCart(sessionId, userId);

    CartResponse userCart = cartService.getOrCreateActiveCart(userId);
    assertThat(userCart.items()).hasSize(1);
    assertThat(userCart.items().get(0).quantity()).isEqualTo(8);
  }

  @Test
  void mergeGuestCartIntoUserCart_noGuestCart_isNoOp() {
    UUID userId = createUserId();

    cartService.mergeGuestCartIntoUserCart(UUID.randomUUID(), userId);

    CartResponse userCart = cartService.getOrCreateActiveCart(userId);
    assertThat(userCart.items()).isEmpty();
  }

  @Test
  void mergeGuestCartIntoUserCart_emptyGuestCart_isNoOp() {
    UUID userId = createUserId();
    UUID sessionId = UUID.randomUUID();
    cartService.getOrCreateGuestCart(sessionId);

    cartService.mergeGuestCartIntoUserCart(sessionId, userId);

    CartResponse userCart = cartService.getOrCreateActiveCart(userId);
    assertThat(userCart.items()).isEmpty();
  }
}
