package io.k2dv.garden.scheduler;

import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.repository.InvoiceRepository;
import io.k2dv.garden.cart.repository.CartRepository;
import io.k2dv.garden.config.AppProperties;
import io.k2dv.garden.quote.model.QuoteRequest;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.quote.repository.QuoteRequestRepository;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class ExpiryScheduler {

    private final QuoteRequestRepository quoteRepo;
    private final InvoiceRepository invoiceRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;
    private final CartRepository cartRepo;
    private final AppProperties props;

    @Scheduled(cron = "0 */15 * * * *")
    @SchedulerLock(name = "expireQuotes", lockAtMostFor = "PT14M", lockAtLeastFor = "PT1M")
    @Transactional
    public void expireQuotes() {
        doExpireQuotes();
    }

    @Transactional
    public void doExpireQuotes() {
        try {
            Instant now = Instant.now();
            List<QuoteRequest> toExpire = new ArrayList<>();
            toExpire.addAll(quoteRepo.findExpiredByStatus(QuoteStatus.SENT, now));
            toExpire.addAll(quoteRepo.findExpiredByStatus(QuoteStatus.PENDING_APPROVAL, now));

            for (QuoteRequest q : toExpire) {
                q.setStatus(QuoteStatus.EXPIRED);
                quoteRepo.save(q);
                userRepo.findById(q.getUserId()).ifPresent(
                    user -> emailService.sendQuoteExpired(user.getEmail(), q.getId()));
            }

            if (!toExpire.isEmpty()) {
                log.info("Expired {} quote(s)", toExpire.size());
            }
        } catch (Exception e) {
            log.error("Failed to expire quotes", e);
        }
    }

    @Scheduled(cron = "0 */15 * * * *")
    @SchedulerLock(name = "markInvoicesOverdue", lockAtMostFor = "PT14M", lockAtLeastFor = "PT1M")
    @Transactional
    public void markInvoicesOverdue() {
        doMarkInvoicesOverdue();
    }

    @Transactional
    public void doMarkInvoicesOverdue() {
        try {
            int count = invoiceRepo.markOverduePastDue(Instant.now());
            if (count > 0) {
                log.info("Marked {} invoice(s) overdue", count);
            }
        } catch (Exception e) {
            log.error("Failed to mark invoices overdue", e);
        }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "purgeAbandonedGuestCarts", lockAtMostFor = "PT50M", lockAtLeastFor = "PT1M")
    @Transactional
    public void purgeAbandonedGuestCarts() {
        try {
            Instant cutoff = Instant.now().minus(Duration.ofDays(props.getCart().getGuestTtlDays()));
            int items = cartRepo.deleteGuestCartItemsOlderThan(cutoff);
            int carts = cartRepo.deleteGuestCartsOlderThan(cutoff);
            if (carts > 0) {
                log.info("Purged {} abandoned guest carts and {} items older than {} days",
                    carts, items, props.getCart().getGuestTtlDays());
            }
        } catch (Exception e) {
            log.error("Failed to purge abandoned guest carts", e);
        }
    }
}
