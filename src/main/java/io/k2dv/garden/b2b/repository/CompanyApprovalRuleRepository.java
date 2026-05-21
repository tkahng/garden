package io.k2dv.garden.b2b.repository;

import io.k2dv.garden.b2b.model.CompanyApprovalRule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CompanyApprovalRuleRepository extends JpaRepository<CompanyApprovalRule, UUID> {
    List<CompanyApprovalRule> findByCompanyId(UUID companyId);
    List<CompanyApprovalRule> findByCompanyIdAndActiveTrue(UUID companyId);
}
