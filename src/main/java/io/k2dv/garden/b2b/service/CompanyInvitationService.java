package io.k2dv.garden.b2b.service;

import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.b2b.dto.CreateInvitationRequest;
import io.k2dv.garden.b2b.dto.InvitationResponse;
import io.k2dv.garden.b2b.model.*;
import io.k2dv.garden.b2b.repository.CompanyInvitationRepository;
import io.k2dv.garden.b2b.repository.CompanyMembershipRepository;
import io.k2dv.garden.b2b.repository.CompanyRepository;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyInvitationService {

    private static final int EXPIRY_DAYS = 7;

    private final CompanyInvitationRepository invitationRepo;
    private final CompanyMembershipRepository membershipRepo;
    private final CompanyRepository companyRepo;
    private final UserRepository userRepo;
    private final EmailService emailService;

    @Transactional
    public InvitationResponse invite(UUID companyId, UUID requestorId, CreateInvitationRequest req) {
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));

        requireOwnerOrManager(companyId, requestorId);

        if (req.role() == CompanyRole.OWNER) {
            throw new ForbiddenException("CANNOT_INVITE_AS_OWNER", "Cannot invite a user as OWNER");
        }

        // Check target is not already a member
        userRepo.findByEmail(req.email()).ifPresent(existing -> {
            if (membershipRepo.existsByCompanyIdAndUserId(companyId, existing.getId())) {
                throw new ConflictException("ALREADY_MEMBER", "This user is already a member of the company");
            }
        });

        // Only one pending invite per email per company
        if (invitationRepo.existsByCompanyIdAndEmailAndStatus(companyId, req.email(), InvitationStatus.PENDING)) {
            throw new ConflictException("INVITATION_ALREADY_PENDING",
                "A pending invitation already exists for this email");
        }

        User inviter = userRepo.findById(requestorId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "Inviting user not found"));

        CompanyInvitation invitation = new CompanyInvitation();
        invitation.setCompanyId(companyId);
        invitation.setEmail(req.email());
        invitation.setRole(req.role() != null ? req.role() : CompanyRole.MEMBER);
        invitation.setSpendingLimit(req.spendingLimit());
        invitation.setToken(UUID.randomUUID());
        invitation.setInvitedBy(requestorId);
        invitation.setExpiresAt(Instant.now().plus(EXPIRY_DAYS, ChronoUnit.DAYS));
        invitation = invitationRepo.save(invitation);

        String inviterName = inviter.getFirstName() + " " + inviter.getLastName();
        emailService.sendCompanyInvitation(req.email(), company.getName(), inviterName,
            invitation.getToken().toString());

        return toResponse(invitation, company.getName());
    }

    @Transactional(readOnly = true)
    public InvitationResponse getByToken(UUID token) {
        CompanyInvitation invitation = requireByToken(token);
        Company company = companyRepo.findById(invitation.getCompanyId()).orElseThrow();
        return toResponse(invitation, company.getName());
    }

    @Transactional
    public InvitationResponse accept(UUID token, UUID userId) {
        CompanyInvitation invitation = requireByToken(token);

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("INVITATION_NOT_PENDING",
                "This invitation is no longer pending (status: " + invitation.getStatus() + ")");
        }
        if (Instant.now().isAfter(invitation.getExpiresAt())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            invitationRepo.save(invitation);
            throw new ConflictException("INVITATION_EXPIRED", "This invitation has expired");
        }

        User user = userRepo.findById(userId)
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found"));

        if (!user.getEmail().equalsIgnoreCase(invitation.getEmail())) {
            throw new ForbiddenException("EMAIL_MISMATCH",
                "This invitation was sent to a different email address");
        }

        if (membershipRepo.existsByCompanyIdAndUserId(invitation.getCompanyId(), userId)) {
            throw new ConflictException("ALREADY_MEMBER", "You are already a member of this company");
        }

        CompanyMembership membership = new CompanyMembership();
        membership.setCompanyId(invitation.getCompanyId());
        membership.setUserId(userId);
        membership.setRole(invitation.getRole());
        membership.setSpendingLimit(invitation.getSpendingLimit());
        membershipRepo.save(membership);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitationRepo.save(invitation);

        Company company = companyRepo.findById(invitation.getCompanyId()).orElseThrow();
        return toResponse(invitation, company.getName());
    }

    @Transactional
    public InvitationResponse cancel(UUID companyId, UUID invitationId, UUID requestorId) {
        requireOwnerOrManager(companyId, requestorId);
        CompanyInvitation invitation = invitationRepo.findById(invitationId)
            .orElseThrow(() -> new NotFoundException("INVITATION_NOT_FOUND", "Invitation not found"));
        if (!invitation.getCompanyId().equals(companyId)) {
            throw new NotFoundException("INVITATION_NOT_FOUND", "Invitation not found");
        }
        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new ConflictException("INVITATION_NOT_PENDING",
                "Can only cancel a pending invitation");
        }
        invitation.setStatus(InvitationStatus.CANCELLED);
        invitationRepo.save(invitation);
        Company company = companyRepo.findById(companyId).orElseThrow();
        return toResponse(invitation, company.getName());
    }

    @Transactional(readOnly = true)
    public List<InvitationResponse> listPending(UUID companyId, UUID requestorId) {
        requireOwnerOrManager(companyId, requestorId);
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        return invitationRepo.findByCompanyIdAndStatus(companyId, InvitationStatus.PENDING)
            .stream().map(i -> toResponse(i, company.getName())).toList();
    }

    private void requireOwnerOrManager(UUID companyId, UUID userId) {
        CompanyMembership m = membershipRepo.findByCompanyIdAndUserId(companyId, userId)
            .orElseThrow(() -> new ForbiddenException("NOT_A_MEMBER", "You are not a member of this company"));
        if (m.getRole() == CompanyRole.MEMBER) {
            throw new ForbiddenException("INSUFFICIENT_COMPANY_ROLE",
                "Only owners and managers can manage invitations");
        }
    }

    private CompanyInvitation requireByToken(UUID token) {
        return invitationRepo.findByToken(token)
            .orElseThrow(() -> new NotFoundException("INVITATION_NOT_FOUND", "Invitation not found"));
    }

    private InvitationResponse toResponse(CompanyInvitation i, String companyName) {
        return new InvitationResponse(
            i.getId(), i.getCompanyId(), companyName, i.getEmail(),
            i.getRole(), i.getSpendingLimit(), i.getToken(),
            i.getInvitedBy(), i.getStatus(), i.getExpiresAt(), i.getCreatedAt()
        );
    }
}
