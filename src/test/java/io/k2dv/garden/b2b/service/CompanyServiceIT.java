package io.k2dv.garden.b2b.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.dto.*;
import io.k2dv.garden.b2b.model.CompanyRole;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompanyServiceIT extends AbstractIntegrationTest {

    @Autowired
    CompanyService companyService;
    @Autowired
    AuthService authService;
    @Autowired
    UserRepository userRepo;
    @MockitoBean
    EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID ownerUserId;
    private UUID memberUserId;

    @BeforeEach
    void setUp() {
        ownerUserId = createUser();
        memberUserId = createUser();
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
        companyService.addMember(c1.id(), ownerUserId, new AddMemberRequest(
            userRepo.findById(memberUserId).orElseThrow().getEmail()));

        CompanyResponse c2 = companyService.create(memberUserId,
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
            new UpdateCompanyRequest("New Name", "TX123", null, null, null, null, null, null, null));

        assertThat(updated.name()).isEqualTo("New Name");
        assertThat(updated.taxId()).isEqualTo("TX123");
    }

    @Test
    void update_byMember_throwsForbidden() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Acme", null, null, null, null, null, null, null, null));
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(
            userRepo.findById(memberUserId).orElseThrow().getEmail()));

        assertThatThrownBy(() -> companyService.update(company.id(), memberUserId,
            new UpdateCompanyRequest("Hacked", null, null, null, null, null, null, null, null)))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void addMember_byOwner_succeeds() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();

        CompanyMemberResponse member = companyService.addMember(company.id(), ownerUserId,
            new AddMemberRequest(memberEmail));

        assertThat(member.userId()).isEqualTo(memberUserId);
        assertThat(member.role()).isEqualTo(CompanyRole.MEMBER);
    }

    @Test
    void addMember_duplicate_throwsConflict() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail));

        assertThatThrownBy(() -> companyService.addMember(company.id(), ownerUserId,
            new AddMemberRequest(memberEmail)))
            .isInstanceOf(ConflictException.class);
    }

    @Test
    void removeMember_byOwner_succeeds() {
        CompanyResponse company = companyService.create(ownerUserId,
            new CreateCompanyRequest("Co", null, null, null, null, null, null, null, null));
        String memberEmail = userRepo.findById(memberUserId).orElseThrow().getEmail();
        companyService.addMember(company.id(), ownerUserId, new AddMemberRequest(memberEmail));

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
            new AddMemberRequest("nobody@unknown.example")))
            .isInstanceOf(NotFoundException.class);
    }
}
