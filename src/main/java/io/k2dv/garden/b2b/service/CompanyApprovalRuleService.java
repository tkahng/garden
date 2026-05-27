package io.k2dv.garden.b2b.service;

import io.k2dv.garden.b2b.dto.CompanyApprovalRuleRequest;
import io.k2dv.garden.b2b.dto.CompanyApprovalRuleResponse;
import io.k2dv.garden.b2b.dto.QuoteApprovalPendencyResponse;
import io.k2dv.garden.b2b.model.CompanyApprovalRule;
import io.k2dv.garden.b2b.model.CompanyRole;
import io.k2dv.garden.b2b.model.QuoteApprovalPendency;
import io.k2dv.garden.b2b.repository.CompanyApprovalRuleRepository;
import io.k2dv.garden.b2b.repository.CompanyMembershipRepository;
import io.k2dv.garden.b2b.repository.QuoteApprovalPendencyRepository;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Manages spend-threshold approval rules for B2B companies and drives the quote
 * approval workflow. When an order or quote exceeds a configured threshold, this
 * service creates {@code QuoteApprovalPendency} records that must be resolved by
 * members with the required role before the order can be finalized.
 */
@Service
@RequiredArgsConstructor
public class CompanyApprovalRuleService {

    private final CompanyApprovalRuleRepository ruleRepo;
    private final QuoteApprovalPendencyRepository pendencyRepo;
    private final CompanyMembershipRepository membershipRepo;

    /**
     * Returns all approval rules configured for the given company, both active and inactive.
     */
    @Transactional(readOnly = true)
    public List<CompanyApprovalRuleResponse> listByCompany(UUID companyId) {
        return ruleRepo.findByCompanyId(companyId).stream().map(this::toResponse).toList();
    }

    /**
     * Creates a new spend-threshold rule for the company, defaulting to active status.
     * The rule fires when an order or quote total meets or exceeds the configured threshold.
     */
    @Transactional
    public CompanyApprovalRuleResponse create(UUID companyId, CompanyApprovalRuleRequest req) {
        CompanyApprovalRule rule = new CompanyApprovalRule();
        rule.setCompanyId(companyId);
        rule.setName(req.name());
        rule.setThresholdAmount(req.thresholdAmount());
        rule.setRequiredRole(req.requiredRole());
        rule.setActive(true);
        return toResponse(ruleRepo.save(rule));
    }

    /**
     * Updates the threshold, required role, or display name of an existing approval rule.
     * Enforces company ownership of the rule before making changes.
     */
    @Transactional
    public CompanyApprovalRuleResponse update(UUID ruleId, UUID companyId, CompanyApprovalRuleRequest req) {
        CompanyApprovalRule rule = ruleRepo.findById(ruleId)
            .orElseThrow(() -> new NotFoundException("RULE_NOT_FOUND", "Approval rule not found"));
        if (!rule.getCompanyId().equals(companyId)) {
            throw new ForbiddenException("WRONG_COMPANY", "Rule does not belong to this company");
        }
        rule.setName(req.name());
        rule.setThresholdAmount(req.thresholdAmount());
        rule.setRequiredRole(req.requiredRole());
        return toResponse(ruleRepo.save(rule));
    }

    /**
     * Permanently deletes an approval rule. Blocked if any quotes have unresolved pendencies
     * tied to this rule; in that case, deactivating via {@link #toggleActive} is preferred.
     */
    @Transactional
    public void delete(UUID ruleId, UUID companyId) {
        CompanyApprovalRule rule = ruleRepo.findById(ruleId)
            .orElseThrow(() -> new NotFoundException("RULE_NOT_FOUND", "Approval rule not found"));
        if (!rule.getCompanyId().equals(companyId)) {
            throw new ForbiddenException("WRONG_COMPANY", "Rule does not belong to this company");
        }
        // Reject if live quotes have unresolved pendencies for this rule
        boolean hasLivePendencies = pendencyRepo.existsByRuleIdAndActionIsNull(ruleId);
        if (hasLivePendencies) {
            throw new ConflictException("RULE_HAS_LIVE_PENDENCIES",
                "Cannot delete an approval rule that has unresolved pending approvals. Deactivate it instead.");
        }
        ruleRepo.delete(rule);
    }

    /**
     * Activates or deactivates an approval rule without deleting it.
     * Inactive rules are ignored by {@link #evaluateAndCreatePendencies}.
     */
    @Transactional
    public void toggleActive(UUID ruleId, UUID companyId, boolean active) {
        CompanyApprovalRule rule = ruleRepo.findById(ruleId)
            .orElseThrow(() -> new NotFoundException("RULE_NOT_FOUND", "Approval rule not found"));
        if (!rule.getCompanyId().equals(companyId)) {
            throw new ForbiddenException("WRONG_COMPANY", "Rule does not belong to this company");
        }
        rule.setActive(active);
        ruleRepo.save(rule);
    }

    /**
     * Evaluates all active rules against the order/quote total and creates a
     * {@code QuoteApprovalPendency} record for each rule that fires.
     * Returns {@code true} if at least one rule triggered (approval required), {@code false} if the
     * order can proceed immediately.
     */
    @Transactional
    public boolean evaluateAndCreatePendencies(UUID quoteId, UUID companyId, BigDecimal totalAmount) {
        List<CompanyApprovalRule> rules = ruleRepo.findByCompanyIdAndActiveTrue(companyId)
            .stream()
            .filter(r -> totalAmount.compareTo(r.getThresholdAmount()) >= 0)
            .toList();

        if (rules.isEmpty()) return false;

        for (CompanyApprovalRule rule : rules) {
            QuoteApprovalPendency p = new QuoteApprovalPendency();
            p.setQuoteId(quoteId);
            p.setRuleId(rule.getId());
            pendencyRepo.save(p);
        }
        return true;
    }

    @Transactional(readOnly = true)
    public List<QuoteApprovalPendencyResponse> getPendencies(UUID quoteId) {
        List<QuoteApprovalPendency> pendencies = pendencyRepo.findByQuoteId(quoteId);
        Set<UUID> ruleIds = pendencies.stream().map(QuoteApprovalPendency::getRuleId).collect(Collectors.toSet());
        Map<UUID, CompanyApprovalRule> rulesById = ruleRepo.findAllById(ruleIds).stream()
            .collect(Collectors.toMap(CompanyApprovalRule::getId, r -> r));
        return pendencies.stream().map(p -> {
            CompanyApprovalRule rule = rulesById.get(p.getRuleId());
            return new QuoteApprovalPendencyResponse(
                p.getId(), p.getRuleId(),
                rule != null ? rule.getName() : null,
                rule != null ? rule.getRequiredRole() : null,
                p.getAction(), p.getResolvedBy(), p.getRejectionReason(), p.getResolvedAt()
            );
        }).toList();
    }

    /**
     * Resolves a single pendency for the given quote and rule.
     * Returns true if all pendencies are now resolved (quote can be finalized).
     */
    @Transactional
    public boolean resolvePendency(UUID quoteId, UUID approverId, UUID ruleId, String action, String rejectionReason) {
        CompanyApprovalRule rule = ruleRepo.findById(ruleId)
            .orElseThrow(() -> new NotFoundException("RULE_NOT_FOUND", "Approval rule not found"));

        boolean hasRole = membershipRepo.findByCompanyIdAndUserId(rule.getCompanyId(), approverId)
            .map(m -> {
                if ("OWNER".equals(rule.getRequiredRole())) return m.getRole() == CompanyRole.OWNER;
                return m.getRole() == CompanyRole.OWNER || m.getRole() == CompanyRole.MANAGER;
            })
            .orElse(false);

        if (!hasRole) {
            throw new ForbiddenException("INSUFFICIENT_ROLE",
                "You do not have the required role (" + rule.getRequiredRole() + ") to approve this rule");
        }

        QuoteApprovalPendency pendency = pendencyRepo.findByQuoteIdAndRuleId(quoteId, ruleId)
            .orElseThrow(() -> new NotFoundException("PENDENCY_NOT_FOUND", "Approval pendency not found"));

        if (pendency.getAction() != null) {
            throw new ConflictException("PENDENCY_ALREADY_RESOLVED",
                "This approval step has already been " + pendency.getAction().toLowerCase());
        }

        pendency.setAction(action);
        pendency.setResolvedBy(approverId);
        pendency.setResolvedAt(Instant.now());
        if (rejectionReason != null) pendency.setRejectionReason(rejectionReason);
        pendencyRepo.save(pendency);

        // All approved → true. Any rejected → false (and quote should be rejected)
        List<QuoteApprovalPendency> allPendencies = pendencyRepo.findByQuoteId(quoteId);
        boolean anyRejected = allPendencies.stream().anyMatch(p -> "REJECTED".equals(p.getAction()));
        if (anyRejected) return false;
        return allPendencies.stream().allMatch(p -> "APPROVED".equals(p.getAction()));
    }

    @Transactional(readOnly = true)
    public List<QuoteApprovalPendency> getUnresolvedPendencies(UUID quoteId) {
        return pendencyRepo.findByQuoteIdAndActionIsNull(quoteId);
    }

    @Transactional(readOnly = true)
    public boolean hasAnyPendingRejection(UUID quoteId) {
        return pendencyRepo.findByQuoteId(quoteId).stream().anyMatch(p -> "REJECTED".equals(p.getAction()));
    }

    private CompanyApprovalRuleResponse toResponse(CompanyApprovalRule r) {
        return new CompanyApprovalRuleResponse(
            r.getId(), r.getCompanyId(), r.getName(),
            r.getThresholdAmount(), r.getRequiredRole(),
            r.isActive(), r.getCreatedAt()
        );
    }
}
