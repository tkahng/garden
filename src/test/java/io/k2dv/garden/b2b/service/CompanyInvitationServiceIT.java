package io.k2dv.garden.b2b.service;

import io.k2dv.garden.auth.dto.RegisterRequest;
import io.k2dv.garden.auth.service.AuthService;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.dto.CreateCompanyRequest;
import io.k2dv.garden.b2b.dto.CreateInvitationRequest;
import io.k2dv.garden.b2b.dto.InvitationResponse;
import io.k2dv.garden.b2b.model.CompanyRole;
import io.k2dv.garden.b2b.model.InvitationStatus;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

class CompanyInvitationServiceIT extends AbstractIntegrationTest {

    @Autowired CompanyInvitationService invitationService;
    @Autowired CompanyService companyService;
    @Autowired AuthService authService;
    @Autowired UserRepository userRepo;
    @MockitoBean EmailService emailService;

    private static final AtomicInteger counter = new AtomicInteger(0);

    private UUID ownerUserId;
    private UUID companyId;
    private String ownerEmail;

    @BeforeEach
    void setUp() {
        doNothing().when(emailService).sendCompanyInvitation(any(), any(), any(), any());

        int n = counter.incrementAndGet();
        ownerEmail = "inv-owner-" + n + "-" + UUID.randomUUID() + "@example.com";
        authService.register(new RegisterRequest(ownerEmail, "password1", "Owner", "User"));
        ownerUserId = userRepo.findByEmail(ownerEmail).orElseThrow().getId();
        companyId = companyService.create(ownerUserId,
            new CreateCompanyRequest("InvCo " + n, null, null, null, null, null, null, null, null)).id();
    }

    private String newEmail() {
        return "invitee-" + counter.incrementAndGet() + "-" + UUID.randomUUID() + "@example.com";
    }

    private UUID registerUser(String email) {
        authService.register(new RegisterRequest(email, "password1", "Inv", "Itee"));
        return userRepo.findByEmail(email).orElseThrow().getId();
    }

    @Test
    void invite_createsPendingInvitation() {
        String email = newEmail();
        InvitationResponse resp = invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, CompanyRole.MEMBER, null));

        assertThat(resp.email()).isEqualTo(email);
        assertThat(resp.status()).isEqualTo(InvitationStatus.PENDING);
        assertThat(resp.role()).isEqualTo(CompanyRole.MEMBER);
        assertThat(resp.token()).isNotNull();
        assertThat(resp.companyName()).isNotBlank();
    }

    @Test
    void invite_asManager_defaultsToMember() {
        // promote someone to manager first
        String managerEmail = newEmail();
        UUID managerId = registerUser(managerEmail);
        companyService.addMember(companyId, ownerUserId,
            new io.k2dv.garden.b2b.dto.AddMemberRequest(managerEmail, null));
        companyService.updateMemberRole(companyId, managerId, ownerUserId,
            new io.k2dv.garden.b2b.dto.UpdateMemberRoleRequest(CompanyRole.MANAGER));

        String inviteeEmail = newEmail();
        InvitationResponse resp = invitationService.invite(companyId, managerId,
            new CreateInvitationRequest(inviteeEmail, null, null));

        assertThat(resp.role()).isEqualTo(CompanyRole.MEMBER);
        assertThat(resp.status()).isEqualTo(InvitationStatus.PENDING);
    }

    @Test
    void invite_withManagerRole_setsRole() {
        String email = newEmail();
        InvitationResponse resp = invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, CompanyRole.MANAGER, new BigDecimal("5000.00")));

        assertThat(resp.role()).isEqualTo(CompanyRole.MANAGER);
        assertThat(resp.spendingLimit()).isEqualByComparingTo("5000.00");
    }

    @Test
    void invite_asOwnerRole_throwsForbidden() {
        assertThatThrownBy(() -> invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(newEmail(), CompanyRole.OWNER, null)))
            .isInstanceOf(ForbiddenException.class)
            .satisfies(e -> assertThat(((ForbiddenException) e).getErrorCode())
                .isEqualTo("CANNOT_INVITE_AS_OWNER"));
    }

    @Test
    void invite_byMember_throwsForbidden() {
        String memberEmail = newEmail();
        UUID memberId = registerUser(memberEmail);
        companyService.addMember(companyId, ownerUserId,
            new io.k2dv.garden.b2b.dto.AddMemberRequest(memberEmail, null));

        assertThatThrownBy(() -> invitationService.invite(companyId, memberId,
            new CreateInvitationRequest(newEmail(), null, null)))
            .isInstanceOf(ForbiddenException.class)
            .satisfies(e -> assertThat(((ForbiddenException) e).getErrorCode())
                .isEqualTo("INSUFFICIENT_COMPANY_ROLE"));
    }

    @Test
    void invite_duplicatePending_throwsConflict() {
        String email = newEmail();
        invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, null, null));

        assertThatThrownBy(() -> invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, null, null)))
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> assertThat(((ConflictException) e).getErrorCode())
                .isEqualTo("INVITATION_ALREADY_PENDING"));
    }

    @Test
    void invite_alreadyMember_throwsConflict() {
        String memberEmail = newEmail();
        registerUser(memberEmail);
        companyService.addMember(companyId, ownerUserId,
            new io.k2dv.garden.b2b.dto.AddMemberRequest(memberEmail, null));

        assertThatThrownBy(() -> invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(memberEmail, null, null)))
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> assertThat(((ConflictException) e).getErrorCode())
                .isEqualTo("ALREADY_MEMBER"));
    }

    @Test
    void getByToken_returnsPendingInvitation() {
        String email = newEmail();
        InvitationResponse created = invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, null, null));

        InvitationResponse found = invitationService.getByToken(created.token());
        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.email()).isEqualTo(email);
    }

    @Test
    void getByToken_unknownToken_throwsNotFound() {
        assertThatThrownBy(() -> invitationService.getByToken(UUID.randomUUID()))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void accept_registeredUser_addsMembership() {
        String email = newEmail();
        UUID inviteeId = registerUser(email);

        InvitationResponse invitation = invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, CompanyRole.MANAGER, new BigDecimal("1000.00")));

        InvitationResponse accepted = invitationService.accept(invitation.token(), inviteeId);

        assertThat(accepted.status()).isEqualTo(InvitationStatus.ACCEPTED);

        // membership should exist with correct role/limit
        var members = companyService.listMembers(companyId, ownerUserId);
        assertThat(members).anyMatch(m ->
            m.userId().equals(inviteeId)
            && m.role() == CompanyRole.MANAGER
            && m.spendingLimit().compareTo(new BigDecimal("1000.00")) == 0);
    }

    @Test
    void accept_wrongEmail_throwsForbidden() {
        String email = newEmail();
        InvitationResponse invitation = invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, null, null));

        String otherEmail = newEmail();
        UUID otherId = registerUser(otherEmail);

        assertThatThrownBy(() -> invitationService.accept(invitation.token(), otherId))
            .isInstanceOf(ForbiddenException.class)
            .satisfies(e -> assertThat(((ForbiddenException) e).getErrorCode())
                .isEqualTo("EMAIL_MISMATCH"));
    }

    @Test
    void accept_alreadyAccepted_throwsConflict() {
        String email = newEmail();
        UUID inviteeId = registerUser(email);
        InvitationResponse invitation = invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, null, null));
        invitationService.accept(invitation.token(), inviteeId);

        assertThatThrownBy(() -> invitationService.accept(invitation.token(), inviteeId))
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> assertThat(((ConflictException) e).getErrorCode())
                .isEqualTo("INVITATION_NOT_PENDING"));
    }

    @Test
    void cancel_byOwner_setsCancelled() {
        String email = newEmail();
        InvitationResponse invitation = invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, null, null));

        InvitationResponse cancelled = invitationService.cancel(companyId, invitation.id(), ownerUserId);
        assertThat(cancelled.status()).isEqualTo(InvitationStatus.CANCELLED);
    }

    @Test
    void cancel_removesFromPendingList() {
        String email = newEmail();
        InvitationResponse invitation = invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, null, null));

        assertThat(invitationService.listPending(companyId, ownerUserId)).hasSize(1);

        invitationService.cancel(companyId, invitation.id(), ownerUserId);

        assertThat(invitationService.listPending(companyId, ownerUserId)).isEmpty();
    }

    @Test
    void cancel_alreadyCancelled_throwsConflict() {
        String email = newEmail();
        InvitationResponse invitation = invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(email, null, null));
        invitationService.cancel(companyId, invitation.id(), ownerUserId);

        assertThatThrownBy(() -> invitationService.cancel(companyId, invitation.id(), ownerUserId))
            .isInstanceOf(ConflictException.class)
            .satisfies(e -> assertThat(((ConflictException) e).getErrorCode())
                .isEqualTo("INVITATION_NOT_PENDING"));
    }

    @Test
    void listPending_returnsOnlyPending() {
        String e1 = newEmail();
        String e2 = newEmail();
        InvitationResponse inv1 = invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(e1, null, null));
        invitationService.invite(companyId, ownerUserId,
            new CreateInvitationRequest(e2, null, null));

        // cancel one
        invitationService.cancel(companyId, inv1.id(), ownerUserId);

        var pending = invitationService.listPending(companyId, ownerUserId);
        assertThat(pending).hasSize(1);
        assertThat(pending.get(0).email()).isEqualTo(e2);
    }
}
