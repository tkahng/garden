package io.k2dv.garden.b2b.service;

import io.k2dv.garden.b2b.dto.CreditAccountResponse;
import io.k2dv.garden.b2b.dto.CreateCreditAccountRequest;
import io.k2dv.garden.b2b.dto.UpdateCreditAccountRequest;
import io.k2dv.garden.b2b.model.CreditAccount;
import io.k2dv.garden.b2b.repository.CompanyRepository;
import io.k2dv.garden.b2b.repository.CreditAccountRepository;
import io.k2dv.garden.b2b.repository.InvoiceRepository;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Manages net-terms credit accounts that allow B2B companies to purchase on credit
 * and pay later via invoices. Each company may have at most one credit account; this
 * service tracks the credit limit, outstanding invoice balance, and payment term days
 * (e.g. NET_30) used when generating invoices.
 */
@Service
@RequiredArgsConstructor
public class CreditAccountService {

    private final CreditAccountRepository creditAccountRepo;
    private final InvoiceRepository invoiceRepo;
    private final CompanyRepository companyRepo;

    /**
     * Opens a new credit account for a company, applying default values of 30 payment-term
     * days and USD currency when not specified. Enforces one-account-per-company.
     */
    @Transactional
    public CreditAccountResponse create(CreateCreditAccountRequest req) {
        companyRepo.findById(req.companyId())
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        if (creditAccountRepo.existsByCompanyId(req.companyId())) {
            throw new ConflictException("CREDIT_ACCOUNT_EXISTS",
                "A credit account already exists for this company");
        }
        CreditAccount account = new CreditAccount();
        account.setCompanyId(req.companyId());
        account.setCreditLimit(req.creditLimit());
        account.setPaymentTermsDays(req.paymentTermsDays() != null ? req.paymentTermsDays() : 30);
        account.setCurrency(req.currency() != null ? req.currency() : "USD");
        return toResponse(creditAccountRepo.save(account));
    }

    @Transactional(readOnly = true)
    public CreditAccountResponse getByCompany(UUID companyId) {
        CreditAccount account = requireByCompany(companyId);
        return toResponse(account);
    }

    /**
     * Updates the credit limit and optionally the payment term days for a company's credit account.
     */
    @Transactional
    public CreditAccountResponse update(UUID companyId, UpdateCreditAccountRequest req) {
        CreditAccount account = requireByCompany(companyId);
        account.setCreditLimit(req.creditLimit());
        if (req.paymentTermsDays() != null) account.setPaymentTermsDays(req.paymentTermsDays());
        return toResponse(creditAccountRepo.save(account));
    }

    /**
     * Removes a company's credit account, reverting the company to pay-at-checkout.
     * Existing invoices are unaffected.
     */
    @Transactional
    public void delete(UUID companyId) {
        CreditAccount account = requireByCompany(companyId);
        creditAccountRepo.delete(account);
    }

    @Transactional(readOnly = true)
    public Optional<CreditAccount> findByCompanyId(UUID companyId) {
        return creditAccountRepo.findByCompanyId(companyId);
    }

    /**
     * Returns the sum of all unpaid invoice amounts for the company, representing
     * the currently drawn-down portion of the credit limit.
     */
    @Transactional(readOnly = true)
    public BigDecimal getOutstandingBalance(UUID companyId) {
        return invoiceRepo.computeOutstandingBalance(companyId);
    }

    /**
     * Returns the number of days until invoice payment is due for this company's credit account,
     * or 0 if the company has no credit account.
     */
    @Transactional(readOnly = true)
    public int getPaymentTermsDays(UUID companyId) {
        return creditAccountRepo.findByCompanyId(companyId)
            .map(CreditAccount::getPaymentTermsDays)
            .orElse(0);
    }

    /**
     * Throws {@link io.k2dv.garden.shared.exception.ValidationException} if the company has a
     * credit account but the order total would exceed the remaining available credit.
     * No-ops if the company has no credit account (pay-at-checkout flow).
     */
    @Transactional(readOnly = true)
    public void assertCreditAvailable(UUID companyId, BigDecimal orderTotal) {
        creditAccountRepo.findByCompanyId(companyId).ifPresent(account -> {
            BigDecimal outstanding = invoiceRepo.computeOutstandingBalance(companyId);
            BigDecimal available = account.getCreditLimit().subtract(outstanding);
            if (orderTotal.compareTo(available) > 0) {
                throw new ValidationException("CREDIT_LIMIT_EXCEEDED",
                    "Order total exceeds available credit. Available: " + available.toPlainString()
                    + ", Requested: " + orderTotal.toPlainString());
            }
        });
    }

    private CreditAccount requireByCompany(UUID companyId) {
        return creditAccountRepo.findByCompanyId(companyId)
            .orElseThrow(() -> new NotFoundException("CREDIT_ACCOUNT_NOT_FOUND",
                "No credit account found for this company"));
    }

    private CreditAccountResponse toResponse(CreditAccount a) {
        BigDecimal outstanding = invoiceRepo.computeOutstandingBalance(a.getCompanyId());
        BigDecimal available = a.getCreditLimit().subtract(outstanding);
        return new CreditAccountResponse(
            a.getId(), a.getCompanyId(), a.getCreditLimit(),
            outstanding, available, a.getPaymentTermsDays(), a.getCurrency(),
            a.getCreatedAt(), a.getUpdatedAt()
        );
    }
}
