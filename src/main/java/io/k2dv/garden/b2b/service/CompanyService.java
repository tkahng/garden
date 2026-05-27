package io.k2dv.garden.b2b.service;

import io.k2dv.garden.b2b.dto.*;
import java.math.BigDecimal;
import io.k2dv.garden.b2b.model.Company;
import io.k2dv.garden.b2b.model.CompanyMembership;
import io.k2dv.garden.b2b.model.CompanyRole;
import io.k2dv.garden.b2b.model.CompanyProductCatalog;
import io.k2dv.garden.b2b.model.InvoiceStatus;
import io.k2dv.garden.b2b.repository.CompanyMembershipRepository;
import io.k2dv.garden.b2b.repository.CompanyProductCatalogRepository;
import io.k2dv.garden.b2b.repository.CompanyRepository;
import io.k2dv.garden.b2b.repository.InvoiceRepository;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.product.repository.ProductRepository;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.shared.exception.ValidationException;
import io.k2dv.garden.user.model.User;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Core B2B service that manages company lifecycle, membership, and spending controls.
 * Handles company creation (which auto-assigns the creator as OWNER), member role management,
 * per-member spending limits, a company-scoped product catalog, and aggregated spending
 * analytics across orders and invoices.
 */
@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companyRepo;
    private final CompanyMembershipRepository membershipRepo;
    private final UserRepository userRepo;
    private final CompanyProductCatalogRepository catalogRepo;
    private final ProductRepository productRepo;
    private final OrderRepository orderRepo;
    private final InvoiceRepository invoiceRepo;

    /**
     * Creates a new company and automatically enrolls the requestor as its OWNER.
     * The OWNER role is the only role that can manage members and mutate company settings.
     */
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

    /**
     * Returns all companies the given user belongs to, regardless of their role within each company.
     */
    @Transactional(readOnly = true)
    public List<CompanyResponse> listForUser(UUID userId) {
        List<UUID> companyIds = membershipRepo.findByUserId(userId).stream()
            .map(CompanyMembership::getCompanyId)
            .toList();
        return companyRepo.findAllById(companyIds).stream()
            .map(this::toResponse)
            .toList();
    }

    /**
     * Retrieves a single company by ID, enforcing that the caller is a member of that company.
     */
    @Transactional(readOnly = true)
    public CompanyResponse getById(UUID companyId, UUID userId) {
        requireMember(companyId, userId);
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        return toResponse(company);
    }

    /**
     * Updates company profile and billing details; restricted to the company OWNER.
     * The optional {@code taxExempt} flag, when set, suppresses tax calculation on future orders.
     */
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

    /**
     * Lists all members of a company along with their roles and spending limits.
     * Accessible to any member of the company.
     */
    @Transactional(readOnly = true)
    public List<CompanyMemberResponse> listMembers(UUID companyId, UUID requestorId) {
        requireMember(companyId, requestorId);
        List<CompanyMembership> memberships = membershipRepo.findByCompanyId(companyId);
        return memberships.stream().map(m -> {
            User user = userRepo.findById(m.getUserId()).orElse(null);
            return toMemberResponse(m, user);
        }).toList();
    }

    /**
     * Directly adds an existing platform user to the company with the MEMBER role.
     * Prefer the invitation flow ({@link CompanyInvitationService}) when the user
     * may not yet have an account. Throws if the user is already a member.
     */
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

    /**
     * Removes a member from the company. The OWNER cannot remove themselves to
     * prevent a company from becoming ownerless.
     */
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

    /**
     * Sets or clears the per-order spending cap for a specific member.
     * A null limit means unlimited spend; a non-null limit is enforced by
     * {@link #assertSpendingLimit} at order placement time.
     */
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

    /**
     * Guard used by other services to assert that the user is a company member;
     * throws {@link io.k2dv.garden.shared.exception.ForbiddenException} otherwise.
     */
    public void requireMemberAccess(UUID companyId, UUID userId) {
        requireMember(companyId, userId);
    }

    /**
     * Admin-only: returns all companies in the platform without access filtering.
     */
    @Transactional(readOnly = true)
    public List<CompanyResponse> listAll() {
        return companyRepo.findAll().stream().map(this::toResponse).toList();
    }

    /**
     * Admin-only: retrieves a company by ID without membership verification.
     */
    @Transactional(readOnly = true)
    public CompanyResponse adminGetById(UUID companyId) {
        return companyRepo.findById(companyId)
            .map(this::toResponse)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
    }

    /**
     * Admin-only: performs a partial update on any company field including the
     * assigned sales rep, which is not exposed in the self-service update endpoint.
     */
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

    /**
     * Replaces the free-form metadata blob on a company, used for custom integrations
     * and back-office annotations.
     */
    @Transactional
    public CompanyResponse updateMetadata(UUID companyId, Map<String, Object> metadata) {
        Company company = companyRepo.findById(companyId)
            .orElseThrow(() -> new NotFoundException("COMPANY_NOT_FOUND", "Company not found"));
        company.setMetadata(metadata);
        return toResponse(companyRepo.save(company));
    }

    /**
     * Returns whether the company holds tax-exempt status; used by the order/tax calculation pipeline.
     */
    @Transactional(readOnly = true)
    public boolean isTaxExempt(UUID companyId) {
        return companyRepo.findById(companyId)
            .map(Company::isTaxExempt)
            .orElse(false);
    }

    /**
     * Returns the per-order spending cap configured for this member, or null if unlimited.
     */
    @Transactional(readOnly = true)
    public BigDecimal getSpendingLimit(UUID companyId, UUID userId) {
        return membershipRepo.findByCompanyIdAndUserId(companyId, userId)
            .map(CompanyMembership::getSpendingLimit)
            .orElse(null);
    }

    /**
     * Throws a {@link io.k2dv.garden.shared.exception.ValidationException} if the order total
     * exceeds the member's configured spending limit; no-ops when no limit is set.
     * Called during order placement to enforce B2B purchasing controls.
     */
    @Transactional(readOnly = true)
    public void assertSpendingLimit(UUID companyId, UUID userId, BigDecimal orderTotal) {
        BigDecimal limit = getSpendingLimit(companyId, userId);
        if (limit != null && orderTotal.compareTo(limit) > 0) {
            throw new ValidationException("SPENDING_LIMIT_EXCEEDED",
                "Order total exceeds your spending limit of " + limit);
        }
    }

    @Transactional(readOnly = true)
    public boolean isOwner(UUID companyId, UUID userId) {
        return membershipRepo.findByCompanyIdAndUserId(companyId, userId)
            .map(m -> m.getRole() == CompanyRole.OWNER)
            .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean isMember(UUID companyId, UUID userId) {
        return membershipRepo.existsByCompanyIdAndUserId(companyId, userId);
    }

    @Transactional(readOnly = true)
    public boolean isOwnerOrManager(UUID companyId, UUID userId) {
        return membershipRepo.findByCompanyIdAndUserId(companyId, userId)
            .map(m -> m.getRole() == CompanyRole.OWNER || m.getRole() == CompanyRole.MANAGER)
            .orElse(false);
    }

    /**
     * Changes a member's role within the company. Promoting to OWNER is disallowed to
     * preserve the single-owner invariant; changing the existing OWNER's role is also blocked.
     */
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

    // ─── Catalog ──────────────────────────────────────────────────────────────

    /**
     * Returns the IDs of all products in the company's curated product catalog.
     * An empty catalog means no restriction; a non-empty catalog implies the company
     * only has access to those listed products.
     */
    @Transactional(readOnly = true)
    public List<UUID> getCatalogProductIds(UUID companyId) {
        assertCompanyExists(companyId);
        return catalogRepo.findProductIdsByCompanyId(companyId);
    }

    /**
     * Adds a product to the company's catalog; idempotent if already present.
     * Validates that the product exists and is not soft-deleted.
     */
    @Transactional
    public void addToCatalog(UUID companyId, UUID productId) {
        assertCompanyExists(companyId);
        productRepo.findByIdAndDeletedAtIsNull(productId)
            .orElseThrow(() -> new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));
        if (!catalogRepo.existsByCompanyIdAndProductId(companyId, productId)) {
            CompanyProductCatalog entry = new CompanyProductCatalog();
            entry.setCompanyId(companyId);
            entry.setProductId(productId);
            catalogRepo.save(entry);
        }
    }

    /**
     * Removes a product from the company's catalog; no-ops if the product was not present.
     */
    @Transactional
    public void removeFromCatalog(UUID companyId, UUID productId) {
        catalogRepo.deleteByCompanyIdAndProductId(companyId, productId);
    }

    private void assertCompanyExists(UUID companyId) {
        if (!companyRepo.existsById(companyId)) {
            throw new NotFoundException("COMPANY_NOT_FOUND", "Company not found");
        }
    }

    /**
     * Builds a comprehensive spending report for a company: total order volume, invoice aging
     * buckets (pending / overdue / paid), and per-member utilization percentages for members
     * who have a spending limit configured. Uses bulk queries to avoid N+1 patterns.
     */
    @Transactional(readOnly = true)
    public CompanySpendingSummaryResponse getSpendingSummary(UUID companyId) {
        if (!companyRepo.existsById(companyId)) {
            throw new NotFoundException("COMPANY_NOT_FOUND", "Company not found");
        }
        Collection<OrderStatus> paidStatuses = Set.of(
            OrderStatus.PAID, OrderStatus.PARTIALLY_FULFILLED,
            OrderStatus.FULFILLED, OrderStatus.INVOICED
        );
        long totalOrders = orderRepo.countByCompanyIdAndStatusIn(companyId, paidStatuses);
        BigDecimal totalSpend = orderRepo.sumSpendByCompanyId(companyId, paidStatuses);

        // Invoice aging
        long pendingCount = invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.ISSUED);
        BigDecimal pendingAmount = invoiceRepo.sumTotalByCompanyIdAndStatus(companyId, InvoiceStatus.ISSUED);
        long overdueCount = invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.OVERDUE);
        BigDecimal overdueAmount = invoiceRepo.sumTotalByCompanyIdAndStatus(companyId, InvoiceStatus.OVERDUE);
        long paidCount = invoiceRepo.countByCompanyIdAndStatus(companyId, InvoiceStatus.PAID);
        BigDecimal paidAmount = invoiceRepo.sumTotalByCompanyIdAndStatus(companyId, InvoiceStatus.PAID);

        // Member spending — bulk-load users and spend totals to avoid N+1 queries
        List<CompanyMembership> membersWithLimit = membershipRepo
            .findByCompanyId(companyId)
            .stream()
            .filter(m -> m.getSpendingLimit() != null)
            .toList();

        List<UUID> memberIds = membersWithLimit.stream().map(CompanyMembership::getUserId).toList();
        Map<UUID, User> usersById = memberIds.isEmpty() ? Map.of()
            : userRepo.findAllById(memberIds).stream().collect(java.util.stream.Collectors.toMap(User::getId, u -> u));
        Map<UUID, BigDecimal> spendByUser = memberIds.isEmpty() ? Map.of()
            : orderRepo.sumSpendByUserIds(memberIds, paidStatuses).stream()
                .collect(java.util.stream.Collectors.toMap(
                    io.k2dv.garden.order.repository.UserSpendProjection::getUserId,
                    io.k2dv.garden.order.repository.UserSpendProjection::getTotalSpend));

        List<CompanySpendingSummaryResponse.MemberSpend> memberSpending = membersWithLimit.stream()
            .map(m -> {
                User user = usersById.get(m.getUserId());
                BigDecimal spend = spendByUser.getOrDefault(m.getUserId(), BigDecimal.ZERO);
                int utilization = m.getSpendingLimit().compareTo(BigDecimal.ZERO) > 0
                    ? spend.multiply(BigDecimal.valueOf(100))
                        .divide(m.getSpendingLimit(), 0, RoundingMode.HALF_UP)
                        .min(BigDecimal.valueOf(100))
                        .intValue()
                    : 0;
                return new CompanySpendingSummaryResponse.MemberSpend(
                    m.getUserId(),
                    user != null ? user.getEmail() : null,
                    spend,
                    m.getSpendingLimit(),
                    utilization
                );
            })
            .toList();

        return new CompanySpendingSummaryResponse(
            totalOrders,
            totalSpend,
            new CompanySpendingSummaryResponse.InvoiceSummary(
                pendingCount, pendingAmount,
                overdueCount, overdueAmount,
                paidCount, paidAmount
            ),
            memberSpending
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
            m.getDepartmentId(),
            m.getCreatedAt()
        );
    }
}
