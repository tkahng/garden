package io.k2dv.garden.scheduler;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.dto.CreateCompanyRequest;
import io.k2dv.garden.b2b.service.CompanyService;
import io.k2dv.garden.quote.model.QuoteRequest;
import io.k2dv.garden.quote.model.QuoteStatus;
import io.k2dv.garden.quote.repository.QuoteRequestRepository;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doNothing;
import static org.mockito.ArgumentMatchers.any;

class ExpirySchedulerQuoteIT extends AbstractIntegrationTest {

    @Autowired ExpiryScheduler scheduler;
    @Autowired QuoteRequestRepository quoteRepo;
    @Autowired AuthService authService;
    @Autowired CompanyService companyService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        doNothing().when(emailService).sendEmailVerification(any(), any());
        int n = counter.incrementAndGet();
        String email = "sched-quote-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Sched", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();
        companyId = companyService.create(userId,
            new CreateCompanyRequest("SchedCo " + n, null, null, null, null, null, null, null, null)).id();
    }

    private QuoteRequest savedQuote(QuoteStatus status, Instant expiresAt) {
        QuoteRequest q = new QuoteRequest();
        q.setUserId(userId);
        q.setCompanyId(companyId);
        q.setStatus(status);
        q.setExpiresAt(expiresAt);
        q.setDeliveryAddressLine1("1 Main St");
        q.setDeliveryCity("Springfield");
        q.setDeliveryPostalCode("12345");
        q.setDeliveryCountry("US");
        return quoteRepo.save(q);
    }

    @Test
    void expireQuotes_transitionsSentPastExpiry() {
        QuoteRequest expired = savedQuote(QuoteStatus.SENT, Instant.now().minus(1, ChronoUnit.HOURS));

        scheduler.expireQuotes();

        assertThat(quoteRepo.findById(expired.getId()).orElseThrow().getStatus())
            .isEqualTo(QuoteStatus.EXPIRED);
    }

    @Test
    void expireQuotes_ignoresSentNotYetExpired() {
        QuoteRequest future = savedQuote(QuoteStatus.SENT, Instant.now().plus(1, ChronoUnit.DAYS));

        scheduler.expireQuotes();

        assertThat(quoteRepo.findById(future.getId()).orElseThrow().getStatus())
            .isEqualTo(QuoteStatus.SENT);
    }

    @Test
    void expireQuotes_ignoresSentWithNoExpiry() {
        QuoteRequest noExpiry = savedQuote(QuoteStatus.SENT, null);

        scheduler.expireQuotes();

        assertThat(quoteRepo.findById(noExpiry.getId()).orElseThrow().getStatus())
            .isEqualTo(QuoteStatus.SENT);
    }

    @Test
    void expireQuotes_doesNotTouchNonSentStatuses() {
        QuoteRequest accepted   = savedQuote(QuoteStatus.ACCEPTED,         Instant.now().minus(1, ChronoUnit.HOURS));
        QuoteRequest pending    = savedQuote(QuoteStatus.PENDING,          Instant.now().minus(1, ChronoUnit.HOURS));
        QuoteRequest draft      = savedQuote(QuoteStatus.DRAFT,            Instant.now().minus(1, ChronoUnit.HOURS));
        QuoteRequest paid       = savedQuote(QuoteStatus.PAID,             Instant.now().minus(1, ChronoUnit.HOURS));
        QuoteRequest cancelled  = savedQuote(QuoteStatus.CANCELLED,        Instant.now().minus(1, ChronoUnit.HOURS));
        QuoteRequest rejected   = savedQuote(QuoteStatus.REJECTED,         Instant.now().minus(1, ChronoUnit.HOURS));
        QuoteRequest approval   = savedQuote(QuoteStatus.PENDING_APPROVAL, Instant.now().minus(1, ChronoUnit.HOURS));

        scheduler.expireQuotes();

        assertThat(quoteRepo.findById(accepted.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.ACCEPTED);
        assertThat(quoteRepo.findById(pending.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.PENDING);
        assertThat(quoteRepo.findById(draft.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.DRAFT);
        assertThat(quoteRepo.findById(paid.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.PAID);
        assertThat(quoteRepo.findById(cancelled.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.CANCELLED);
        assertThat(quoteRepo.findById(rejected.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.REJECTED);
        assertThat(quoteRepo.findById(approval.getId()).orElseThrow().getStatus()).isEqualTo(QuoteStatus.PENDING_APPROVAL);
    }

    @Test
    void expireQuotes_alreadyExpiredQuoteUntouched() {
        QuoteRequest alreadyExpired = savedQuote(QuoteStatus.EXPIRED, Instant.now().minus(1, ChronoUnit.HOURS));

        scheduler.expireQuotes();

        assertThat(quoteRepo.findById(alreadyExpired.getId()).orElseThrow().getStatus())
            .isEqualTo(QuoteStatus.EXPIRED);
    }
}
