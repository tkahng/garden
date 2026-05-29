package io.k2dv.garden.b2b.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.b2b.model.CompanyRole;
import io.k2dv.garden.blob.service.StorageService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class CompanyServiceIT extends AbstractIntegrationTest {

    @Autowired CompanyService companyService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;
    @MockitoBean StorageService storageService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID ownerUserId;
    private UUID memberUserId;
    private UUID managerUserId;

    @BeforeEach
    void setUp() {
        ownerUserId = createUser();
        memberUserId = createUser();
        managerUserId = createUser();
    }

    private UUID createUser() {
        int n = counter.incrementAndGet();
        String email = "b2b-test-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(email, "password1", "Test", "User"));
        return userRepo.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void create_setsOwnerMembership() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Acme Corp", null, null, null, null, null, null, null, null));

        assertThat(company.id()).isNotNull();
        assertThat(company.name()).isEqualTo("Acme Corp");

        List<CompanyMemberResponse> members = companyService.listMembers(company.id(), ownerUserId);
        assertThat(members).hasSize(1);
        assertThat(members.get(0).role()).isEqualTo(CompanyRole.OWNER);
    }

    @Test
    void listForUser_returnsOwnedAndMemberCompanies() {
        CompanyResponse c1 = companyService.create(ownerUserId,
            new CreateCompanyRequest("Company A", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(c1.id(), ownerUserId, new AddMemberRequest(memberEmail, null));

        companyService.create(memberUserId,
            new CreateCompanyRequest("Company B", null, null, null, null, null, null, null, null));

        List<CompanyResponse> forOwner = companyService.listForUser(ownerUserId);
        assertThat(forOwner).extracting(CompanyResponse::name).contains("Company A");

        List<CompanyResponse> forMember = companyService.listForUser(memberUserId);
        assertThat(forMember).extracting(CompanyResponse::name).containsExactlyInAnyOrder("Company A", "Company B");
    }

    @Test
    void update_byOwner_succeeds() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Old Name", null, null, null, null, null, null, null, null));

        CompanyResponse updated = companyService.update(company.id(), ownerUserId,
            new UpdateCompanyRequest("New Name", "TX123", null, null, null, null, null, null, null, null));

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.taxId()).isEqualTo("TX123");
    }

    @Test
    void update_byMember_throwsForbidden() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Acme", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail, null));

        assertThatThrownBy(() -> companyService.update(company.id(), memberUserId,
            new UpdateCompanyRequest("Hacked", null, null, null, null, null, null, null, null, null)))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addMember_byOwner_succeeds() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();

        CompanyMemberResponse member = companyService.addMember(company.id(), ownerUserId,
            new AddMemberRequest(memberEmail, null));

        assertThat(member.userId()).isEqualTo(memberUserId);
        assertThat(member.role()).isEqualTo(CompanyRole.MEMBER);
        assertThat(member.spendingLimit()).isNull();
    }

    @Test
    void addMember_withSpendingLimit_setsLimit() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();

        CompanyMemberResponse member = companyService.addMember(company.id(), ownerUserId,
            new AddMemberRequest(memberEmail, new BigDecimal("5000.00")));

        assertThat(member.spendingLimit()).isEqualByComparingTo("5000.00");
    }

    @Test
    void updateSpendingLimit_byOwner_updatesLimit() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail, null));

        CompanyMemberResponse updated = companyService.updateSpendingLimit(
            company.id(), memberUserId, ownerUserId,
            new UpdateSpendingLimitRequest(new BigDecimal("2500.00")));

        assertThat(updated.spendingLimit()).isEqualByComparingTo("2500.00");
    }

    @Test
    void addMember_duplicate_throwsConflict() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail, null));

        assertThatThrownBy(() -> companyService.addMember(company.id(), ownerUserId,
            new AddMemberRequest(memberEmail, null)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void removeMember_byOwner_succeeds() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail, null));

        companyService.removeMember(company.id(), ownerUserId, memberUserId);

        List<CompanyMemberResponse> members = companyService.listMembers(company.id(), ownerUserId);
        assertThat(members).hasSize(1); // only owner remains
    }

    @Test
    void removeMember_self_throwsConflict() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> companyService.removeMember(company.id(), ownerUserId, ownerUserId))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void getById_nonMember_throwsForbidden() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> companyService.getById(company.id(), memberUserId))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addMember_unknownEmail_throwsNotFound() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> companyService.addMember(company.id(), ownerUserId,
            new AddMemberRequest("nobody@unknown.example", null)))
            .isInstanceOf(NotFoundException.class);
    }

    // --- Member role management ---

    @Test
    void updateMemberRole_promotesToManager() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail, null));

        CompanyMemberResponse updated = companyService.updateMemberRole(
            company.id(), memberUserId, ownerUserId,
            new UpdateMemberRoleRequest(CompanyRole.MANAGER));

        assertThat(updated.role()).isEqualTo(CompanyRole.MANAGER);
    }

    @Test
    void updateMemberRole_demotesToMember() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail, null));
        companyService.updateMemberRole(company.id(), memberUserId, ownerUserId,
            new UpdateMemberRoleRequest(CompanyRole.MANAGER));

        CompanyMemberResponse demoted = companyService.updateMemberRole(
            company.id(), memberUserId, ownerUserId,
            new UpdateMemberRoleRequest(CompanyRole.MEMBER));

        assertThat(demoted.role()).isEqualTo(CompanyRole.MEMBER);
    }

    @Test
    void updateMemberRole_cannotPromoteToOwner_throwsForbidden() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail, null));

        assertThatThrownBy(() -> companyService.updateMemberRole(
            company.id(), memberUserId, ownerUserId,
            new UpdateMemberRoleRequest(CompanyRole.OWNER)))
            .isInstanceOf(ForbiddenException.class)
            .satisfies(e -> assertThat(((ForbiddenException) e).getErrorCode()).isEqualTo("CANNOT_ASSIGN_OWNER"));
    }

    @Test
    void updateMemberRole_cannotChangeOwnerRole_throwsForbidden() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));

        assertThatThrownBy(() -> companyService.updateMemberRole(
            company.id(), ownerUserId, ownerUserId,
            new UpdateMemberRoleRequest(CompanyRole.MEMBER)))
            .isInstanceOf(ForbiddenException.class)
            .satisfies(e -> assertThat(((ForbiddenException) e).getErrorCode()).isEqualTo("CANNOT_CHANGE_OWNER_ROLE"));
    }

    @Test
    void updateMemberRole_byNonOwner_throwsForbidden() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail, null));

        assertThatThrownBy(() -> companyService.updateMemberRole(
            company.id(), ownerUserId, memberUserId,
            new UpdateMemberRoleRequest(CompanyRole.MEMBER)))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void getSpendingSummary_noOrdersNoInvoices_returnsZeroes() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Summary Co", null, null, null, null, null, null, null, null));

        CompanySpendingSummaryResponse summary = companyService.getSpendingSummary(company.id());

        assertThat(summary.totalOrders()).isZero();
        assertThat(summary.totalSpend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(summary.invoiceSummary().pendingCount()).isZero();
        assertThat(summary.invoiceSummary().overdueCount()).isZero();
        assertThat(summary.invoiceSummary().paidCount()).isZero();
        assertThat(summary.memberSpending()).isEmpty();
    }

    @Test
    void getSpendingSummary_withMemberLimit_includesMemberSpending() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Spend Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId,
            new AddMemberRequest(memberEmail, new BigDecimal("1000.00")));

        CompanySpendingSummaryResponse summary = companyService.getSpendingSummary(company.id());

        assertThat(summary.memberSpending()).hasSize(1);
        CompanySpendingSummaryResponse.MemberSpend spend = summary.memberSpending().get(0);
        assertThat(spend.userId()).isEqualTo(memberUserId);
        assertThat(spend.spendingLimit()).isEqualByComparingTo(new BigDecimal("1000.00"));
        assertThat(spend.totalSpend()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(spend.utilizationPercent()).isZero();
    }

    @Test
    void getSpendingSummary_unknownCompany_throwsNotFound() {
        assertThatThrownBy(() -> companyService.getSpendingSummary(UUID.randomUUID()))
            .isInstanceOf(io.k2dv.garden.shared.exception.NotFoundException.class);
    }

    @Test
    void getSpendingSummary_defaultCurrencyIsUSD() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Curr Co", null, null, null, null, null, null, null, null));

        CompanySpendingSummaryResponse summary = companyService.getSpendingSummary(company.id());

        assertThat(summary.currency()).isEqualTo("USD");
    }

    // ─── Tax certificate upload ───────────────────────────────────────────────

    @Test
    void uploadTaxCertificate_byOwner_succeeds() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Cert Co", null, null, null, null, null, null, null, null));
        MockMultipartFile pdf = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[100]);

        companyService.uploadTaxCertificate(company.id(), ownerUserId, pdf);

        verify(storageService).store(anyString(), eq("application/pdf"), any(), eq(100L));
    }

    @Test
    void uploadTaxCertificate_byManager_succeeds() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Cert Co", null, null, null, null, null, null, null, null));
        String managerEmail = userRepo.findById(managerUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(managerEmail, null));
        companyService.updateMemberRole(company.id(), managerUserId, ownerUserId,
            new UpdateMemberRoleRequest(CompanyRole.MANAGER));
        MockMultipartFile pdf = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[50]);

        companyService.uploadTaxCertificate(company.id(), managerUserId, pdf);

        verify(storageService).store(anyString(), eq("application/pdf"), any(), eq(50L));
    }

    @Test
    void uploadTaxCertificate_byMember_throwsForbidden() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Cert Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail, null));
        MockMultipartFile pdf = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[100]);

        assertThatThrownBy(() -> companyService.uploadTaxCertificate(company.id(), memberUserId, pdf))
            .isInstanceOf(ForbiddenException.class)
            .satisfies(e -> assertThat(((ForbiddenException) e).getErrorCode()).isEqualTo("NOT_OWNER_OR_MANAGER"));
    }

    @Test
    void uploadTaxCertificate_invalidMimeType_throwsValidation() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Cert Co", null, null, null, null, null, null, null, null));
        MockMultipartFile exe = new MockMultipartFile("file", "cert.exe", "application/octet-stream", new byte[100]);

        assertThatThrownBy(() -> companyService.uploadTaxCertificate(company.id(), ownerUserId, exe))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).getErrorCode()).isEqualTo("INVALID_FILE_TYPE"));
    }

    @Test
    void uploadTaxCertificate_oversizedFile_throwsValidation() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Cert Co", null, null, null, null, null, null, null, null));
        byte[] oversized = new byte[10_485_761]; // 10 MB + 1 byte
        MockMultipartFile huge = new MockMultipartFile("file", "cert.pdf", "application/pdf", oversized);

        assertThatThrownBy(() -> companyService.uploadTaxCertificate(company.id(), ownerUserId, huge))
            .isInstanceOf(ValidationException.class)
            .satisfies(e -> assertThat(((ValidationException) e).getErrorCode()).isEqualTo("FILE_TOO_LARGE"));
    }

    @Test
    void uploadTaxCertificate_replacingExisting_deletesOldKey() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Cert Co", null, null, null, null, null, null, null, null));
        MockMultipartFile pdf = new MockMultipartFile("file", "cert.pdf", "application/pdf", new byte[100]);

        companyService.uploadTaxCertificate(company.id(), ownerUserId, pdf);
        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(storageService, times(1)).store(keyCaptor.capture(), anyString(), any(), anyLong());
        String firstKey = keyCaptor.getValue();

        companyService.uploadTaxCertificate(company.id(), ownerUserId, pdf);
        verify(storageService).delete(firstKey);
    }
}
