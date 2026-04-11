package io.k2dv.garden.quote.service;

import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.model.Company;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.quote.model.QuoteItem;
import io.k2dv.garden.quote.model.QuoteRequest;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class QuotePdfServiceTest extends AbstractIntegrationTest {

    @Autowired
    QuotePdfService pdfService;

    @MockitoBean
    EmailService emailService;
    @MockitoBean
    StorageService storageService;

    @Test
    void generate_returnsPdfBytes() {
        QuoteRequest quote = buildQuote();
        List<QuoteItem> items = List.of(buildItem("Widget A", 2, new BigDecimal("99.99")));
        Company company = buildCompany();

        byte[] pdf = pdfService.generate(quote, items, company);

        assertThat(pdf).isNotEmpty();
        // PDF files start with %PDF
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generate_withNullExpiry_doesNotThrow() {
        QuoteRequest quote = buildQuote();
        quote.setExpiresAt(null);
        List<QuoteItem> items = List.of(buildItem("Item", 1, new BigDecimal("50.00")));

        byte[] pdf = pdfService.generate(quote, items, buildCompany());

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void generate_withMultipleItems_producesNonEmptyPdf() {
        QuoteRequest quote = buildQuote();
        List<QuoteItem> items = List.of(
            buildItem("Product A", 3, new BigDecimal("25.00")),
            buildItem("Custom Freight", 1, new BigDecimal("100.00")));

        byte[] pdf = pdfService.generate(quote, items, buildCompany());

        assertThat(pdf).isNotEmpty();
    }

    @Test
    void generate_twoItemsWithStaffPrices_contentCorrect() throws Exception {
        QuoteRequest quote = buildQuote();
        // Item 1: "Industrial Pump" qty 2 @ $150.00 → line total $300.00
        // Item 2: "Custom Freight"  qty 1 @ $75.00  → line total $75.00
        // Subtotal: $375.00
        List<QuoteItem> items = List.of(
            buildItem("Industrial Pump", 2, new BigDecimal("150.00")),
            buildItem("Custom Freight",  1, new BigDecimal("75.00")));

        byte[] pdf = pdfService.generate(quote, items, buildCompany());

        String text;
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            text = new PDFTextStripper().getText(doc);
        }

        assertThat(text).contains("Industrial Pump");
        assertThat(text).contains("Custom Freight");
        assertThat(text).contains("150.00");  // unit price of item 1
        assertThat(text).contains("300.00");  // line total: 2 × 150.00
        assertThat(text).contains("75.00");   // unit price and line total of item 2
        assertThat(text).contains("375.00");  // subtotal
        assertThat(text).contains("Taxes will be calculated at checkout");
    }

    private QuoteRequest buildQuote() {
        QuoteRequest q = new QuoteRequest();
        q.setUserId(UUID.randomUUID());
        q.setCompanyId(UUID.randomUUID());
        q.setStatus(QuoteStatus.SENT);
        q.setDeliveryAddressLine1("123 Main St");
        q.setDeliveryCity("Springfield");
        q.setDeliveryPostalCode("12345");
        q.setDeliveryCountry("US");
        q.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
        return q;
    }

    private QuoteItem buildItem(String description, int qty, BigDecimal unitPrice) {
        QuoteItem item = new QuoteItem();
        item.setQuoteRequestId(UUID.randomUUID());
        item.setDescription(description);
        item.setQuantity(qty);
        item.setUnitPrice(unitPrice);
        return item;
    }

    private Company buildCompany() {
        Company c = new Company();
        c.setName("Acme Corp");
        c.setTaxId("TX123");
        c.setBillingAddressLine1("456 Business Ave");
        c.setBillingCity("Metropolis");
        c.setBillingPostalCode("67890");
        c.setBillingCountry("US");
        return c;
    }
}
