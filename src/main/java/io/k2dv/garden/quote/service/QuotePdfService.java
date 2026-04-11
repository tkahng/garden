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

@Service
@RequiredArgsConstructor
public class QuotePdfService {

    private static final DateTimeFormatter DATE_FMT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneId.of("UTC"));

    private final TemplateEngine templateEngine;

    public byte[] generate(QuoteRequest quote, List<QuoteItem> items, Company company) {
        BigDecimal grandTotal = items.stream()
            .filter(i -> i.getUnitPrice() != null)
            .map(i -> i.getUnitPrice().multiply(BigDecimal.valueOf(i.getQuantity())))
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        Context ctx = new Context();
        ctx.setVariable("quote", quote);
        ctx.setVariable("items", items);
        ctx.setVariable("company", company);
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
