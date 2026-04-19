package io.k2dv.garden.b2b.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CreditAccountServiceIT extends AbstractIntegrationTest {

    @Autowired CreditAccountService creditAccountService;
    @Autowired CompanyService companyService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID userId;
    private UUID companyId;

    @BeforeEach
    void setUp() {
        int n = counter.incrementAndGet();
        String email = "credit-test-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        userId = userRepo.findByEmail(email).orElseThrow().getId();
        companyId = companyService.create(userId,
            new CreateCompanyRequest("CredCo " + n, null, null, null, null, null, null, null, null)).id();
    }

    @Test
    void create_setsDefaultsAndReturnsResponse() {
        var resp = creditAccountService.create(
            new CreateCreditAccountRequest(companyId, new BigDecimal("10000.00"), null, null));

        assertThat(resp.companyId()).isEqualTo(companyId);
        assertThat(resp.creditLimit()).isEqualByComparingTo("10000.00");
        assertThat(resp.paymentTermsDays()).isEqualTo(30);
        assertThat(resp.currency()).isEqualTo("USD");
        assertThat(resp.outstandingBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(resp.availableCredit()).isEqualByComparingTo("10000.00");
    }

    @Test
    void create_withExplicitTermsAndCurrency() {
        var resp = creditAccountService.create(
            new CreateCreditAccountRequest(companyId, new BigDecimal("5000.00"), 45, "EUR"));

        assertThat(resp.paymentTermsDays()).isEqualTo(45);
        assertThat(resp.currency()).isEqualTo("EUR");
    }

    @Test
    void create_duplicateCompany_throwsConflict() {
        creditAccountService.create(
            new CreateCreditAccountRequest(companyId, new BigDecimal("1000.00"), null, null));

        assertThatThrownBy(() -> creditAccountService.create(
            new CreateCreditAccountRequest(companyId, new BigDecimal("2000.00"), null, null)))
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> assertThat(((ConflictException) e).getErrorCode()).isEqualTo("CREDIT_ACCOUNT_EXISTS"));
    }

    @Test
    void create_unknownCompany_throwsNotFound() {
        assertThatThrownBy(() -> creditAccountService.create(
            new CreateCreditAccountRequest(UUID.randomUUID(), new BigDecimal("1000.00"), null, null)))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getByCompany_afterCreate_returnsIt() {
        creditAccountService.create(
            new CreateCreditAccountRequest(companyId, new BigDecimal("3000.00"), null, null));

        var resp = creditAccountService.getByCompany(companyId);
        assertThat(resp.creditLimit()).isEqualByComparingTo("3000.00");
    }

    @Test
    void getByCompany_noAccount_throwsNotFound() {
        assertThatThrownBy(() -> creditAccountService.getByCompany(companyId))
            .isInstanceOf(NotFoundException.class)
            .satisfies(e -> assertThat(((NotFoundException) e).getErrorCode()).isEqualTo("CREDIT_ACCOUNT_NOT_FOUND"));
    }

    @Test
    void update_changesCreditLimit() {
        creditAccountService.create(
            new CreateCreditAccountRequest(companyId, new BigDecimal("1000.00"), null, null));

        var resp = creditAccountService.update(companyId,
            new UpdateCreditAccountRequest(new BigDecimal("9999.00"), 60));

        assertThat(resp.creditLimit()).isEqualByComparingTo("9999.00");
        assertThat(resp.paymentTermsDays()).isEqualTo(60);
    }

    @Test
    void delete_removesAccount() {
        creditAccountService.create(
            new CreateCreditAccountRequest(companyId, new BigDecimal("500.00"), null, null));
        creditAccountService.delete(companyId);

        assertThat(creditAccountService.findByCompanyId(companyId)).isEmpty();
    }

    @Test
    void delete_noAccount_throwsNotFound() {
        assertThatThrownBy(() -> creditAccountService.delete(companyId))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void outstandingBalance_noInvoices_returnsZero() {
        assertThat(creditAccountService.getOutstandingBalance(companyId))
            .isEqualByComparingTo(BigDecimal.ZERO);
    }
}
