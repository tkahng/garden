package io.k2dv.garden.b2b.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import io.k2dv.garden.b2b.model.Company;
import io.k2dv.garden.b2b.model.Invoice;
import io.k2dv.garden.b2b.model.InvoicePayment;
import io.k2dv.garden.b2b.repository.CompanyRepository;
import io.k2dv.garden.b2b.repository.InvoicePaymentRepository;
import io.k2dv.garden.b2b.repository.InvoiceRepository;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderItem;
import io.k2dv.garden.order.repository.OrderItemRepository;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.product.model.Product;
import io.k2dv.garden.product.model.ProductVariant;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.product.repository.ProductVariantRepository;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InvoicePdfService {

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));

    private final TemplateEngine templateEngine;
    private final InvoiceRepository invoiceRepo;
    private final InvoicePaymentRepository paymentRepo;
    private final OrderRepository orderRepo;
    private final OrderItemRepository orderItemRepo;
    private final ProductVariantRepository variantRepo;
    private final ProductRepository productRepo;
    private final CompanyRepository companyRepo;

    @Transactional(readOnly = true)
    public byte[] generate(UUID invoiceId) {
        Invoice invoice = invoiceRepo.findById(invoiceId)
            .orElseThrow(() -> new NotFoundException("INVOICE_NOT_FOUND", "Invoice not found"));

        Order order = orderRepo.findById(invoice.getOrderId())
            .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));

        Company company = companyRepo.findById(invoice.getCompanyId()).orElse(null);

        List<InvoicePayment> payments = paymentRepo.findByInvoiceIdOrderByPaidAtAsc(invoiceId);

        List<OrderItem> rawItems = orderItemRepo.findByOrderId(order.getId());
        List<LineItemRow> lineItems = enrichItems(rawItems);

        BigDecimal subtotal = lineItems.stream()
            .map(LineItemRow::lineTotal)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<PaymentRow> paymentRows = payments.stream()
            .map(p -> new PaymentRow(
                DATE_FMT.format(p.getPaidAt()),
                p.getPaymentReference(),
                p.getAmount(),
                p.getNotes()))
            .toList();

        Context ctx = new Context();
        ctx.setVariable("invoice", invoice);
        ctx.setVariable("order", order);
        ctx.setVariable("company", company);
        ctx.setVariable("paymentRows", paymentRows);
        ctx.setVariable("lineItems", lineItems);
        ctx.setVariable("subtotal", subtotal);
        ctx.setVariable("issuedDate", DATE_FMT.format(invoice.getIssuedAt()));
        ctx.setVariable("dueDate", DATE_FMT.format(invoice.getDueAt()));

        String html = templateEngine.process("invoice-template", ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate invoice PDF", e);
        }
    }

    private List<LineItemRow> enrichItems(List<OrderItem> items) {
        Set<UUID> variantIds = items.stream()
            .map(OrderItem::getVariantId).filter(Objects::nonNull).collect(Collectors.toSet());
        Map<UUID, ProductVariant> variantsById = variantRepo.findAllById(variantIds).stream()
            .collect(Collectors.toMap(ProductVariant::getId, v -> v));
        Set<UUID> productIds = variantsById.values().stream()
            .map(ProductVariant::getProductId).collect(Collectors.toSet());
        Map<UUID, Product> productsById = productRepo.findAllById(productIds).stream()
            .collect(Collectors.toMap(Product::getId, p -> p));

        return items.stream().map(i -> {
            String description = "Item";
            if (i.getVariantId() != null) {
                ProductVariant v = variantsById.get(i.getVariantId());
                if (v != null) {
                    Product p = productsById.get(v.getProductId());
                    if (p != null) {
                        description = p.getTitle() + (v.getTitle() != null ? " — " + v.getTitle() : "");
                    }
                }
            }
            BigDecimal lineTotal = i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()));
            return new LineItemRow(description, i.getQuantity(), i.getUnitPrice(), lineTotal);
        }).toList();
    }

    public record LineItemRow(String description, int quantity, BigDecimal unitPrice, BigDecimal lineTotal) {}

    public record PaymentRow(String paidAt, String paymentReference, BigDecimal amount, String notes) {}
}
