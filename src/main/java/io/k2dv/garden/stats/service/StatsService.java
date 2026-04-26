package io.k2dv.garden.stats.service;

import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.repository.OrderRepository;
import io.k2dv.garden.stats.dto.StatsResponse;
import io.k2dv.garden.stats.dto.TimeSeriesPoint;
import io.k2dv.garden.stats.dto.TopCustomerEntry;
import io.k2dv.garden.stats.dto.TopProductEntry;
import io.k2dv.garden.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

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

    @Transactional(readOnly = true)
    public List<TimeSeriesPoint> getTimeSeries(Instant from, Instant to) {
        return orderRepo.findRevenueTimeSeries(from, to).stream()
            .map(row -> new TimeSeriesPoint(
                (String) row[0],
                ((Number) row[1]).longValue(),
                new BigDecimal(row[2].toString())
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TopProductEntry> getTopProducts(Instant from, Instant to, int limit) {
        return orderRepo.findTopProducts(from, to, limit).stream()
            .map(row -> new TopProductEntry(
                UUID.fromString(row[0].toString()),
                (String) row[1],
                (String) row[2],
                ((Number) row[3]).longValue(),
                new BigDecimal(row[4].toString())
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public List<TopCustomerEntry> getTopCustomers(Instant from, Instant to, int limit) {
        return orderRepo.findTopCustomers(from, to, limit).stream()
            .map(row -> new TopCustomerEntry(
                UUID.fromString(row[0].toString()),
                (String) row[1],
                (String) row[2],
                (String) row[3],
                ((Number) row[4]).longValue(),
                new BigDecimal(row[5].toString())
            ))
            .toList();
    }
}
