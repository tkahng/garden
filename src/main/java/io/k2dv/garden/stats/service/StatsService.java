package io.k2dv.garden.stats.service;

import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.stats.dto.StatsResponse;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final OrderRepository orderRepo;
    private final UserRepository userRepo;

    @Transactional(readOnly = true)
    public StatsResponse getStats(Instant from, Instant to) {
        if (from.isAfter(to)) {
            throw new io.k2dv.garden.shared.exception.ValidationException(
                "INVALID_DATE_RANGE", "from must not be after to");
        }
        long orderCount = orderRepo.countPaidOrdersBetween(from, to, OrderStatus.PAID);
        BigDecimal totalRevenue = orderRepo.sumRevenueForPaidOrdersBetween(from, to, OrderStatus.PAID);
        if (totalRevenue == null) totalRevenue = BigDecimal.ZERO;

        BigDecimal averageOrderValue = orderCount > 0
            ? totalRevenue.divide(BigDecimal.valueOf(orderCount), 4, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        long newCustomerCount = userRepo.countUsersCreatedBetween(from, to);

        return new StatsResponse(from, to, orderCount, totalRevenue, averageOrderValue, newCustomerCount);
    }
}
