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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditAccountService {

    private final CreditAccountRepository creditAccountRepo;
    private final InvoiceRepository invoiceRepo;
    private final CompanyRepository companyRepo;

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

    @Transactional
    public CreditAccountResponse update(UUID companyId, UpdateCreditAccountRequest req) {
        CreditAccount account = requireByCompany(companyId);
        account.setCreditLimit(req.creditLimit());
        if (req.paymentTermsDays() != null) account.setPaymentTermsDays(req.paymentTermsDays());
        return toResponse(creditAccountRepo.save(account));
    }

    @Transactional
    public void delete(UUID companyId) {
        CreditAccount account = requireByCompany(companyId);
        creditAccountRepo.delete(account);
    }

    @Transactional(readOnly = true)
    public Optional<CreditAccount> findByCompanyId(UUID companyId) {
        return creditAccountRepo.findByCompanyId(companyId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getOutstandingBalance(UUID companyId) {
        return invoiceRepo.computeOutstandingBalance(companyId);
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
