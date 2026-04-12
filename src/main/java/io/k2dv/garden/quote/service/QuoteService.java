package io.k2dv.garden.quote.service;

import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.model.Company;
import io.k2dv.garden.b2b.repository.CompanyMembershipRepository;
import io.k2dv.garden.b2b.repository.CompanyRepository;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.blob.model.BlobObject;
import io.k2dv.garden.blob.repository.BlobObjectRepository;
import io.k2dv.garden.blob.config.StorageProperties;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.payment.dto.CheckoutResponse;
import io.k2dv.garden.payment.service.PaymentService;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.quote.dto.*;
import io.k2dv.garden.quote.model.*;
import io.k2dv.garden.quote.repository.*;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class QuoteService {

    private final QuoteRequestRepository quoteRepo;
    private final QuoteItemRepository itemRepo;
    private final QuoteCartService cartService;
    private final CompanyRepository companyRepo;
    private final CompanyMembershipRepository membershipRepo;
    private final ProductVariantRepository variantRepo;
    private final OrderService orderService;
    private final PaymentService paymentService;
    private final QuotePdfService pdfService;
    private final EmailService emailService;
    private final UserRepository userRepo;
    private final BlobObjectRepository blobRepo;
    private final StorageService storageService;
    private final StorageProperties storageProperties;
    private final AppProperties appProperties;

    @Transactional
    public QuoteRequestResponse submit(UUID userId, SubmitQuoteRequest req) {
        // Verify company exists, then membership
        companyRepo.findById(req.companyId())
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        if (!membershipRepo.existsByCompanyIdAndUserId(req.companyId(), userId)) {
            throw new ForbiddenException("NOT_A_MEMBER", "You are not a member of this company");
        }

        QuoteCart cart = cartService.requireActiveCart(userId);
        List<io.k2dv.garden.quote.model.QuoteCartItem> cartItems = cartService.getCartItems(cart.getId());
        if (cartItems.isEmpty()) {
            throw new ValidationException("EMPTY_QUOTE_CART", "Quote cart is empty");
        }

        QuoteRequest quote = new QuoteRequest();
        quote.setUserId(userId);
        quote.setCompanyId(req.companyId());
        quote.setDeliveryAddressLine1(req.deliveryAddressLine1());
        quote.setDeliveryAddressLine2(req.deliveryAddressLine2());
        quote.setDeliveryCity(req.deliveryCity());
        quote.setDeliveryState(req.deliveryState());
        quote.setDeliveryPostalCode(req.deliveryPostalCode());
        quote.setDeliveryCountry(req.deliveryCountry());
        quote.setShippingRequirements(req.shippingRequirements());
        quote.setCustomerNotes(req.customerNotes());
        quote = quoteRepo.save(quote);

        // Copy cart items to quote items
        for (io.k2dv.garden.quote.model.QuoteCartItem cartItem : cartItems) {
            ProductVariant variant = variantRepo.findById(cartItem.getVariantId()).orElse(null);
            String description = variant != null ? variant.getTitle() : "Unknown item";

            QuoteItem quoteItem = new QuoteItem();
            quoteItem.setQuoteRequestId(quote.getId());
            quoteItem.setVariantId(cartItem.getVariantId());
            quoteItem.setDescription(description);
            quoteItem.setQuantity(cartItem.getQuantity());
            // unitPrice is null — will be set by staff
            itemRepo.save(quoteItem);
        }

        // Submit the cart
        cartService.markSubmitted(cart.getId());

        // Send confirmation email to user
        User user = userRepo.findById(userId).orElse(null);
        if (user != null) {
            emailService.sendQuoteSubmitted(user.getEmail(), quote.getId());
        }

        // Notify admin if configured
        String adminEmail = appProperties.getAdminNotificationEmail();
        if (adminEmail != null && !adminEmail.isBlank()) {
            emailService.sendQuoteNewRequest(adminEmail, quote.getId());
        }

        return toResponse(quote);
    }

    @Transactional(readOnly = true)
    public PagedResult<QuoteRequestResponse> listForUser(UUID userId, Pageable pageable) {
        Specification<QuoteRequest> spec = (root, query, cb) ->
            cb.equal(root.get("userId"), userId);
        return PagedResult.of(quoteRepo.findAll(spec, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public QuoteRequestResponse getForUser(UUID quoteId, UUID userId) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        if (!quote.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_YOUR_QUOTE", "This quote does not belong to you");
        }
        return toResponse(quote);
    }

    @Transactional(readOnly = true)
    public byte[] downloadPdf(UUID quoteId, UUID userId) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        if (!quote.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_YOUR_QUOTE", "This quote does not belong to you");
        }
        if (quote.getPdfBlobId() == null) {
            throw new NotFoundException("PDF_NOT_AVAILABLE", "Quote PDF has not been generated yet");
        }
        BlobObject blob = blobRepo.findById(quote.getPdfBlobId())
            .orElseThrow(() -> new NotFoundException("PDF_NOT_FOUND", "Quote PDF not found"));
        try (java.io.InputStream stream = storageService.fetch(storageProperties.getPrivateBucket(), blob.getKey())) {
            return stream.readAllBytes();
        } catch (java.io.IOException e) {
            throw new RuntimeException("Failed to retrieve quote PDF", e);
        }
    }

    // Accept: creates Order + Stripe session, transitions to ACCEPTED
    @Transactional
    public QuoteAcceptResponse accept(UUID quoteId, UUID userId) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        if (!quote.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_YOUR_QUOTE", "This quote does not belong to you");
        }
        if (quote.getStatus() != QuoteStatus.SENT) {
            throw new ConflictException("INVALID_QUOTE_STATUS",
                "Quote must be in SENT status to accept (current: " + quote.getStatus() + ")");
        }
        // Lazy expiry check
        if (quote.getExpiresAt() != null && Instant.now().isAfter(quote.getExpiresAt())) {
            quote.setStatus(QuoteStatus.EXPIRED);
            quoteRepo.save(quote);
            throw new ConflictException("QUOTE_EXPIRED", "This quote has expired");
        }

        List<QuoteItem> items = itemRepo.findByQuoteRequestId(quoteId);
        Order order = orderService.createFromQuote(quote, items);

        quote.setOrderId(order.getId());
        quote.setStatus(QuoteStatus.ACCEPTED);
        quoteRepo.save(quote);

        CheckoutResponse checkout = paymentService.createCheckoutSessionFromQuote(order, items, quote);
        return new QuoteAcceptResponse(checkout.checkoutUrl(), order.getId());
    }

    @Transactional
    public QuoteRequestResponse reject(UUID quoteId, UUID userId) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        if (!quote.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_YOUR_QUOTE", "This quote does not belong to you");
        }
        if (quote.getStatus() != QuoteStatus.SENT) {
            throw new ConflictException("INVALID_QUOTE_STATUS",
                "Quote must be in SENT status to reject");
        }
        quote.setStatus(QuoteStatus.REJECTED);
        return toResponse(quoteRepo.save(quote));
    }

    // --- Admin operations ---

    @Transactional(readOnly = true)
    public PagedResult<QuoteRequestResponse> listAll(QuoteFilter filter, Pageable pageable) {
        Specification<QuoteRequest> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter.status() != null) predicates.add(cb.equal(root.get("status"), filter.status()));
            if (filter.companyId() != null) predicates.add(cb.equal(root.get("companyId"), filter.companyId()));
            if (filter.assignedStaffId() != null)
                predicates.add(cb.equal(root.get("assignedStaffId"), filter.assignedStaffId()));
            if (filter.userId() != null) predicates.add(cb.equal(root.get("userId"), filter.userId()));
            return cb.and(predicates.toArray(new Predicate[0]));
        };
        return PagedResult.of(quoteRepo.findAll(spec, pageable), this::toResponse);
    }

    @Transactional(readOnly = true)
    public QuoteRequestResponse getAdmin(UUID quoteId) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        return toResponse(quote);
    }

    @Transactional
    public QuoteRequestResponse assign(UUID quoteId, AssignStaffRequest req) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        if (quote.getStatus() != QuoteStatus.PENDING && quote.getStatus() != QuoteStatus.ASSIGNED) {
            throw new ConflictException("INVALID_QUOTE_STATUS",
                "Can only assign PENDING or ASSIGNED quotes");
        }
        quote.setAssignedStaffId(req.staffUserId());
        quote.setStatus(QuoteStatus.ASSIGNED);
        return toResponse(quoteRepo.save(quote));
    }

    @Transactional
    public QuoteItemResponse updateItem(UUID quoteId, UUID itemId, UpdateQuoteItemRequest req) {
        requireEditableStatus(quoteId);
        QuoteItem item = itemRepo.findByIdAndQuoteRequestId(itemId, quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_ITEM_NOT_FOUND", "Quote item not found"));
        item.setQuantity(req.quantity());
        item.setUnitPrice(req.unitPrice());
        return toItemResponse(itemRepo.save(item));
    }

    @Transactional
    public QuoteItemResponse addItem(UUID quoteId, AddQuoteItemRequest req) {
        requireEditableStatus(quoteId);
        QuoteItem item = new QuoteItem();
        item.setQuoteRequestId(quoteId);
        item.setDescription(req.description());
        item.setQuantity(req.quantity());
        item.setUnitPrice(req.unitPrice());
        return toItemResponse(itemRepo.save(item));
    }

    @Transactional
    public void removeItem(UUID quoteId, UUID itemId) {
        requireEditableStatus(quoteId);
        QuoteItem item = itemRepo.findByIdAndQuoteRequestId(itemId, quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_ITEM_NOT_FOUND", "Quote item not found"));
        itemRepo.delete(item);
    }

    @Transactional
    public QuoteRequestResponse updateNotes(UUID quoteId, UpdateStaffNotesRequest req) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        quote.setStaffNotes(req.staffNotes());
        return toResponse(quoteRepo.save(quote));
    }

    // Send: generate PDF, email user, transition to SENT
    @Transactional
    public QuoteRequestResponse send(UUID quoteId, SendQuoteRequest req) {
        QuoteRequest quote = loadForSend(quoteId);
        List<QuoteItem> items = itemRepo.findByQuoteRequestId(quoteId);

        // Validate all unit prices set
        boolean anyNull = items.stream().anyMatch(i -> i.getUnitPrice() == null);
        if (anyNull) {
            throw new ValidationException("MISSING_UNIT_PRICE",
                "All line items must have a unit price before sending");
        }
        if (items.isEmpty()) {
            throw new ValidationException("EMPTY_QUOTE", "Quote has no items");
        }

        Company company = companyRepo.findById(quote.getCompanyId())
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));

        quote.setExpiresAt(req.expiresAt());

        // Generate PDF
        byte[] pdfBytes = pdfService.generate(quote, items, company);

        // Upload PDF to private blob storage
        String key = "quotes/quote-" + quoteId + ".pdf";
        storageService.store(storageProperties.getPrivateBucket(), key, "application/pdf",
            new ByteArrayInputStream(pdfBytes), pdfBytes.length);
        BlobObject blob = new BlobObject();
        blob.setKey(key);
        blob.setFilename("quote-" + quoteId + ".pdf");
        blob.setContentType("application/pdf");
        blob.setSize(pdfBytes.length);
        blob = blobRepo.save(blob);
        quote.setPdfBlobId(blob.getId());

        quote.setStatus(QuoteStatus.SENT);
        quoteRepo.save(quote);

        // Email user with PDF attachment
        User user = userRepo.findById(quote.getUserId()).orElse(null);
        if (user != null) {
            emailService.sendQuotePdf(user.getEmail(), quoteId, pdfBytes);
        }

        return toResponse(quote);
    }

    @Transactional
    public QuoteRequestResponse cancel(UUID quoteId) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        if (quote.getStatus() == QuoteStatus.ACCEPTED || quote.getStatus() == QuoteStatus.PAID
            || quote.getStatus() == QuoteStatus.REJECTED
            || quote.getStatus() == QuoteStatus.EXPIRED || quote.getStatus() == QuoteStatus.CANCELLED) {
            throw new ConflictException("INVALID_QUOTE_STATUS",
                "Cannot cancel quote in status: " + quote.getStatus());
        }
        quote.setStatus(QuoteStatus.CANCELLED);
        return toResponse(quoteRepo.save(quote));
    }

    @Transactional
    public QuoteRequestResponse cancelForUser(UUID quoteId, UUID userId) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        if (!quote.getUserId().equals(userId)) {
            throw new ForbiddenException("NOT_YOUR_QUOTE", "This quote does not belong to you");
        }
        if (quote.getStatus() == QuoteStatus.ACCEPTED || quote.getStatus() == QuoteStatus.PAID
            || quote.getStatus() == QuoteStatus.REJECTED
            || quote.getStatus() == QuoteStatus.EXPIRED || quote.getStatus() == QuoteStatus.CANCELLED) {
            throw new ConflictException("INVALID_QUOTE_STATUS",
                "Cannot cancel quote in status: " + quote.getStatus());
        }
        quote.setStatus(QuoteStatus.CANCELLED);
        return toResponse(quoteRepo.save(quote));
    }

    // --- Helpers ---

    private QuoteRequest requireEditableStatus(UUID quoteId) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        if (quote.getStatus() == QuoteStatus.SENT || quote.getStatus() == QuoteStatus.ACCEPTED
            || quote.getStatus() == QuoteStatus.PAID || quote.getStatus() == QuoteStatus.REJECTED
            || quote.getStatus() == QuoteStatus.EXPIRED || quote.getStatus() == QuoteStatus.CANCELLED) {
            throw new ConflictException("INVALID_QUOTE_STATUS",
                "Cannot edit items on quote in status: " + quote.getStatus());
        }
        return quote;
    }

    private QuoteRequest loadForSend(UUID quoteId) {
        QuoteRequest quote = quoteRepo.findById(quoteId)
            .orElseThrow(() -> new NotFoundException("QUOTE_NOT_FOUND", "Quote not found"));
        if (quote.getStatus() != QuoteStatus.PENDING && quote.getStatus() != QuoteStatus.ASSIGNED
            && quote.getStatus() != QuoteStatus.DRAFT) {
            throw new ConflictException("INVALID_QUOTE_STATUS",
                "Can only send PENDING, ASSIGNED, or DRAFT quotes");
        }
        return quote;
    }

    private QuoteRequestResponse toResponse(QuoteRequest q) {
        List<QuoteItem> items = itemRepo.findByQuoteRequestId(q.getId());
        List<QuoteItemResponse> itemResponses = items.stream().map(this::toItemResponse).toList();
        return new QuoteRequestResponse(
            q.getId(), q.getUserId(), q.getCompanyId(), q.getAssignedStaffId(),
            q.getStatus(),
            q.getDeliveryAddressLine1(), q.getDeliveryAddressLine2(),
            q.getDeliveryCity(), q.getDeliveryState(),
            q.getDeliveryPostalCode(), q.getDeliveryCountry(),
            q.getShippingRequirements(), q.getCustomerNotes(), q.getStaffNotes(),
            q.getExpiresAt(), q.getPdfBlobId(), q.getOrderId(),
            itemResponses, q.getCreatedAt(), q.getUpdatedAt()
        );
    }

    private QuoteItemResponse toItemResponse(QuoteItem i) {
        return new QuoteItemResponse(i.getId(), i.getVariantId(), i.getDescription(),
            i.getQuantity(), i.getUnitPrice(), i.getCreatedAt());
    }
}
