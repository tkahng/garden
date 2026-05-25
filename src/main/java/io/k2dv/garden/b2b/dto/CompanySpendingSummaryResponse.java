package io.k2dv.garden.b2b.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

public record CompanySpendingSummaryResponse(
    long totalOrders,
    BigDecimal totalSpend,
    InvoiceSummary invoiceSummary,
    List<MemberSpend> memberSpending
) {
    public record InvoiceSummary(
        long pendingCount,
        BigDecimal pendingAmount,
        long overdueCount,
        BigDecimal overdueAmount,
        long paidCount,
        BigDecimal paidAmount
    ) {}

    public record MemberSpend(
        UUID userId,
        String email,
        BigDecimal totalSpend,
        BigDecimal spendingLimit,
        int utilizationPercent
    ) {}
}
