package io.k2dv.garden.quote.service;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

import io.k2dv.garden.b2b.model.Company;
import io.k2dv.garden.quote.model.QuoteItem;
import io.k2dv.garden.quote.model.QuoteRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates a PDF document for a quote by rendering a Thymeleaf HTML template and converting
 * it to PDF via OpenHTMLToPDF. The resulting bytes are returned to the caller for storage and
 * email delivery; this service has no side effects of its own.
 */
@Service
@RequiredArgsConstructor
public class QuotePdfService {

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));

    private final TemplateEngine templateEngine;

    /**
     * Renders the {@code quote-template} Thymeleaf template with the given quote, line items,
     * and company details, then converts the resulting HTML to a PDF byte array. Line totals
     * and the grand total are computed here and injected into the template context.
     */
    public byte[] generate(QuoteRequest quote, List<QuoteItem> items, Company company) {
        List<BigDecimal> lineTotals = items.stream()
            .map(i -> i.getUnitPrice() != null
                ? i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity()))
                : BigDecimal.ZERO)
            .collect(Collectors.toList());
        BigDecimal grandTotal = lineTotals.stream().reduce(BigDecimal.ZERO, BigDecimal::add);

        Context ctx = new Context();
        ctx.setVariable("quote", quote);
        ctx.setVariable("items", items);
        ctx.setVariable("company", company);
        ctx.setVariable("lineTotals", lineTotals);
        ctx.setVariable("grandTotal", grandTotal);
        ctx.setVariable("issuedDate", DATE_FMT.format(Instant.now()));
        ctx.setVariable("expiryDate",
            quote.getExpiresAt() != null ? DATE_FMT.format(quote.getExpiresAt()) : "N/A");

        String html = templateEngine.process("quote-template", ctx);

        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate quote PDF", e);
        }
    }
}
