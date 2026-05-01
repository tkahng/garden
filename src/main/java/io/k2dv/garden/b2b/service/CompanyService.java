package io.k2dv.garden.b2b.service;

import io.k2dv.garden.b2b.dto.*;
import java.math.BigDecimal;
import io.k2dv.garden.b2b.model.Company;
import io.k2dv.garden.b2b.model.CompanyMembership;
import io.k2dv.garden.b2b.model.CompanyRole;
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

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepo;
    private final CompanyMembershipRepository membershipRepo;
    private final UserRepository userRepo;

    @Transactional
    public CompanyResponse create(UUID requestorId, CreateCompanyRequest req) {
        Company company = new Company();
        company.setName(req.name());
        company.setTaxId(req.taxId());
        company.setPhone(req.phone());
        company.setBillingAddressLine1(req.billingAddressLine1());
        company.setBillingAddressLine2(req.billingAddressLine2());
        company.setBillingCity(req.billingCity());
        company.setBillingState(req.billingState());
        company.setBillingPostalCode(req.billingPostalCode());
        company.setBillingCountry(req.billingCountry());
        company = companyRepo.save(company);

        CompanyMembership membership = new CompanyMembership();
        membership.setCompanyId(company.getId());
        membership.setUserId(requestorId);
        membership.setRole(CompanyRole.OWNER);
        membershipRepo.save(membership);

        return toResponse(company);
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> listForUser(UUID userId) {
        List<UUID> companyIds = membershipRepo.findByUserId(userId).stream()
            .map(CompanyMembership::getCompanyId)
            .toList();
        return companyRepo.findAllById(companyIds).stream()
            .map(this::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse getById(UUID companyId, UUID userId) {
        requireMember(companyId, userId);
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        return toResponse(company);
    }

    @Transactional
    public CompanyResponse update(UUID companyId, UUID requestorId, UpdateCompanyRequest req) {
        requireOwner(companyId, requestorId);
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        company.setName(req.name());
        company.setTaxId(req.taxId());
        company.setPhone(req.phone());
        company.setBillingAddressLine1(req.billingAddressLine1());
        company.setBillingAddressLine2(req.billingAddressLine2());
        company.setBillingCity(req.billingCity());
        company.setBillingState(req.billingState());
        company.setBillingPostalCode(req.billingPostalCode());
        company.setBillingCountry(req.billingCountry());
        if (req.taxExempt() != null) company.setTaxExempt(req.taxExempt());
        return toResponse(companyRepo.save(company));
    }

    @Transactional(readOnly = true)
    public List<CompanyMemberResponse> listMembers(UUID companyId, UUID requestorId) {
        requireMember(companyId, requestorId);
        List<CompanyMembership> memberships = membershipRepo.findByCompanyId(companyId);
        return memberships.stream().map(m -> {
            User user = userRepo.findById(m.getUserId()).orElse(null);
            return toMemberResponse(m, user);
        }).toList();
    }

    @Transactional
    public CompanyMemberResponse addMember(UUID companyId, UUID requestorId, AddMemberRequest req) {
        requireOwner(companyId, requestorId);
        User user = userRepo.findByEmail(req.email())
            .orElseThrow(() -> new NotFoundException("USER_NOT_FOUND", "User not found: " + req.email()));
        if (membershipRepo.existsByCompanyIdAndUserId(companyId, user.getId())) {
            throw new ConflictException("ALREADY_MEMBER", "User is already a member of this company");
        }
        CompanyMembership membership = new CompanyMembership();
        membership.setCompanyId(companyId);
        membership.setUserId(user.getId());
        membership.setRole(CompanyRole.MEMBER);
        membership.setSpendingLimit(req.spendingLimit());
        membership = membershipRepo.save(membership);
        return toMemberResponse(membership, user);
    }

    @Transactional
    public void removeMember(UUID companyId, UUID requestorId, UUID targetUserId) {
        requireOwner(companyId, requestorId);
        if (requestorId.equals(targetUserId)) {
            throw new ConflictException("CANNOT_REMOVE_SELF", "Owner cannot remove themselves from the company");
        }
        CompanyMembership membership = membershipRepo.findByCompanyIdAndUserId(companyId, targetUserId)
            .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found"));
        membershipRepo.delete(membership);
    }

    @Transactional
    public CompanyMemberResponse updateSpendingLimit(UUID companyId, UUID targetUserId, UUID requestorId, UpdateSpendingLimitRequest req) {
        requireOwner(companyId, requestorId);
        CompanyMembership membership = membershipRepo.findByCompanyIdAndUserId(companyId, targetUserId)
            .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found"));
        membership.setSpendingLimit(req.spendingLimit());
        membership = membershipRepo.save(membership);
        User user = userRepo.findById(targetUserId).orElse(null);
        return toMemberResponse(membership, user);
    }

    public void requireMemberAccess(UUID companyId, UUID userId) {
        requireMember(companyId, userId);
    }

    @Transactional(readOnly = true)
    public List<CompanyResponse> listAll() {
        return companyRepo.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public CompanyResponse adminGetById(UUID companyId) {
        return companyRepo.findById(companyId)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
    }

    @Transactional
    public CompanyResponse adminUpdate(UUID companyId, AdminUpdateCompanyRequest req) {
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        if (req.name() != null) company.setName(req.name());
        if (req.taxId() != null) company.setTaxId(req.taxId());
        if (req.phone() != null) company.setPhone(req.phone());
        if (req.billingAddressLine1() != null) company.setBillingAddressLine1(req.billingAddressLine1());
        if (req.billingAddressLine2() != null) company.setBillingAddressLine2(req.billingAddressLine2());
        if (req.billingCity() != null) company.setBillingCity(req.billingCity());
        if (req.billingState() != null) company.setBillingState(req.billingState());
        if (req.billingPostalCode() != null) company.setBillingPostalCode(req.billingPostalCode());
        if (req.billingCountry() != null) company.setBillingCountry(req.billingCountry());
        if (req.taxExempt() != null) company.setTaxExempt(req.taxExempt());
        company.setSalesRepUserId(req.salesRepUserId());
        return toResponse(companyRepo.save(company));
    }

    @Transactional
    public CompanyResponse updateMetadata(UUID companyId, Map<String, Object> metadata) {
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        company.setMetadata(metadata);
        return toResponse(companyRepo.save(company));
    }

    @Transactional(readOnly = true)
    public boolean isTaxExempt(UUID companyId) {
        return companyRepo.findById(companyId)
            .map(Company::isTaxExempt)
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public java.math.BigDecimal getSpendingLimit(UUID companyId, UUID userId) {
        return membershipRepo.findByCompanyIdAndUserId(companyId, userId)
            .map(CompanyMembership::getSpendingLimit)
            .orElse(null);
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID companyId, UUID userId) {
        return membershipRepo.findByCompanyIdAndUserId(companyId, userId)
            .map(m -> m.getRole() == CompanyRole.OWNER)
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isOwnerOrManager(UUID companyId, UUID userId) {
        return membershipRepo.findByCompanyIdAndUserId(companyId, userId)
            .map(m -> m.getRole() == CompanyRole.OWNER || m.getRole() == CompanyRole.MANAGER)
            .orElse(false);
    }

    @Transactional
    public CompanyMemberResponse updateMemberRole(UUID companyId, UUID targetUserId, UUID requestorId,
                                                   UpdateMemberRoleRequest req) {
        requireOwner(companyId, requestorId);
        if (req.role() == CompanyRole.OWNER) {
            throw new ForbiddenException("CANNOT_ASSIGN_OWNER", "Cannot promote a member to OWNER");
        }
        CompanyMembership membership = membershipRepo.findByCompanyIdAndUserId(companyId, targetUserId)
            .orElseThrow(() -> new NotFoundException("MEMBER_NOT_FOUND", "Member not found"));
        if (membership.getRole() == CompanyRole.OWNER) {
            throw new ForbiddenException("CANNOT_CHANGE_OWNER_ROLE", "Cannot change the role of the company owner");
        }
        membership.setRole(req.role());
        membership = membershipRepo.save(membership);
        User user = userRepo.findById(targetUserId).orElse(null);
        return toMemberResponse(membership, user);
    }

    private void requireMember(UUID companyId, UUID userId) {
        if (!membershipRepo.existsByCompanyIdAndUserId(companyId, userId)) {
            throw new ForbiddenException("NOT_A_MEMBER", "You are not a member of this company");
        }
    }

    private void requireOwner(UUID companyId, UUID userId) {
        CompanyMembership membership = membershipRepo.findByCompanyIdAndUserId(companyId, userId)
            .orElseThrow(() -> new ForbiddenException("NOT_A_MEMBER", "You are not a member of this company"));
        if (membership.getRole() != CompanyRole.OWNER) {
            throw new ForbiddenException("NOT_OWNER", "Only the company owner can perform this action");
        }
    }

    private CompanyResponse toResponse(Company c) {
        return new CompanyResponse(
            c.getId(), c.getName(), c.getTaxId(), c.getPhone(),
            c.getBillingAddressLine1(), c.getBillingAddressLine2(),
            c.getBillingCity(), c.getBillingState(),
            c.getBillingPostalCode(), c.getBillingCountry(),
            c.isTaxExempt(), c.getSalesRepUserId(), c.getMetadata(),
            c.getCreatedAt(), c.getUpdatedAt()
        );
    }

    private CompanyMemberResponse toMemberResponse(CompanyMembership m, User user) {
        return new CompanyMemberResponse(
            m.getId(),
            m.getUserId(),
            user != null ? user.getEmail() : null,
            user != null ? user.getFirstName() : null,
            user != null ? user.getLastName() : null,
            m.getRole(),
            m.getSpendingLimit(),
            m.getCreatedAt()
        );
    }
}
