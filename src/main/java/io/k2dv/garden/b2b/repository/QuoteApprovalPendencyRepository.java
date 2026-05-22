package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.QuoteApprovalPendency;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface QuoteApprovalPendencyRepository extends JpaRepository<QuoteApprovalPendency, UUID> {
    List<QuoteApprovalPendency> findByQuoteId(UUID quoteId);
    List<QuoteApprovalPendency> findByQuoteIdAndActionIsNull(UUID quoteId);
    Optional<QuoteApprovalPendency> findByQuoteIdAndRuleId(UUID quoteId, UUID ruleId);
    boolean existsByRuleIdAndActionIsNull(UUID ruleId);
}
