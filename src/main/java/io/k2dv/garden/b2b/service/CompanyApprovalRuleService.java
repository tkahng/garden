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
import io.k2dv.garden.shared.exception.ForbiddenException;
import io.k2dv.garden.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CompanyApprovalRuleService {

    private final CompanyApprovalRuleRepository ruleRepo;
    private final QuoteApprovalPendencyRepository pendencyRepo;
    private final CompanyMembershipRepository membershipRepo;

    @Transactional(readOnly = true)
    public List<CompanyApprovalRuleResponse> listByCompany(UUID companyId) {
        return ruleRepo.findByCompanyId(companyId).stream().map(this::toResponse).toList();
    }

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

    @Transactional
    public void delete(UUID ruleId, UUID companyId) {
        CompanyApprovalRule rule = ruleRepo.findById(ruleId)
            .orElseThrow(() -> new NotFoundException("RULE_NOT_FOUND", "Approval rule not found"));
        if (!rule.getCompanyId().equals(companyId)) {
            throw new ForbiddenException("WRONG_COMPANY", "Rule does not belong to this company");
        }
        ruleRepo.delete(rule);
    }

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

    /** Returns true and creates pendency records if any rules fire; false if no rules apply. */
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
        return pendencyRepo.findByQuoteId(quoteId).stream().map(p -> {
            CompanyApprovalRule rule = ruleRepo.findById(p.getRuleId()).orElse(null);
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
