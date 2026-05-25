package io.k2dv.garden.b2b.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public record CompanyApprovalRuleRequest(
    @NotBlank String name,
    @NotNull @DecimalMin("0.01") BigDecimal thresholdAmount,
    @NotNull @Pattern(regexp = "MANAGER|OWNER") String requiredRole
) {}
