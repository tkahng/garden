package io.k2dv.garden.scheduler;

import io.k2dv.garden.b2b.repository.InvoiceRepository;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.quote.repository.QuoteRequestRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryScheduler {

    private final QuoteRequestRepository quoteRepo;
    private final InvoiceRepository invoiceRepo;

    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void expireQuotes() {
        try {
            int count = quoteRepo.expireByStatus(QuoteStatus.SENT, QuoteStatus.EXPIRED, Instant.now());
            if (count > 0) {
                log.info("Expired {} quote(s)", count);
            }
        } catch (Exception e) {
            log.error("Failed to expire quotes", e);
        }
    }

    @Scheduled(cron = "0 */15 * * * *")
    @Transactional
    public void markInvoicesOverdue() {
        try {
            int count = invoiceRepo.markOverduePastDue(Instant.now());
            if (count > 0) {
                log.info("Marked {} invoice(s) overdue", count);
            }
        } catch (Exception e) {
            log.error("Failed to mark invoices overdue", e);
        }
    }
}
