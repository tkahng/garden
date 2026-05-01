package io.k2dv.garden.b2b.service;

import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.model.Company;
import io.k2dv.garden.b2b.model.Invoice;
import io.k2dv.garden.b2b.model.InvoiceStatus;
import io.k2dv.garden.b2b.repository.CompanyRepository;
import io.k2dv.garden.b2b.repository.InvoiceRepository;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.order.model.Order;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderRepository;
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

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePdfServiceTest extends AbstractIntegrationTest {

    @Autowired InvoicePdfService pdfService;
    @Autowired InvoiceRepository invoiceRepo;
    @Autowired OrderRepository orderRepo;
    @Autowired CompanyRepository companyRepo;

    @MockitoBean EmailService emailService;
    @MockitoBean StorageService storageService;

    @Test
    void generate_returnsPdfBytes() throws Exception {
        Company company = savedCompany();
        Order order = savedOrder();
        Invoice invoice = savedInvoice(company, order);

        byte[] pdf = pdfService.generate(invoice.getId());

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    void generate_pdfContainsInvoiceMetadata() throws Exception {
        Company company = savedCompany();
        Order order = savedOrder();
        Invoice invoice = savedInvoice(company, order);

        byte[] pdf = pdfService.generate(invoice.getId());

        String text;
        try (PDDocument doc = PDDocument.load(new ByteArrayInputStream(pdf))) {
            text = new PDFTextStripper().getText(doc);
        }

        assertThat(text).contains(company.getName());
        assertThat(text).contains("Invoice");
    }

    private Company savedCompany() {
        Company c = new Company();
        c.setName("Test Corp PDF");
        c.setTaxId("TAX-PDF-001");
        c.setBillingAddressLine1("1 PDF Lane");
        c.setBillingCity("Testville");
        c.setBillingPostalCode("12345");
        c.setBillingCountry("US");
        return companyRepo.save(c);
    }

    private Order savedOrder() {
        Order o = new Order();
        o.setStatus(OrderStatus.INVOICED);
        o.setTotalAmount(new BigDecimal("250.00"));
        o.setCurrency("USD");
        return orderRepo.save(o);
    }

    private Invoice savedInvoice(Company company, Order order) {
        Invoice inv = new Invoice();
        inv.setCompanyId(company.getId());
        inv.setOrderId(order.getId());
        inv.setTotalAmount(order.getTotalAmount());
        inv.setPaidAmount(BigDecimal.ZERO);
        inv.setCurrency("USD");
        inv.setStatus(InvoiceStatus.ISSUED);
        inv.setIssuedAt(Instant.now());
        inv.setDueAt(Instant.now().plus(30, ChronoUnit.DAYS));
        return invoiceRepo.save(inv);
    }
}
