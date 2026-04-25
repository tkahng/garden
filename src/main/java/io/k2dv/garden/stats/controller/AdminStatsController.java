package io.k2dv.garden.stats.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.stats.dto.StatsResponse;
import io.k2dv.garden.stats.dto.TimeSeriesPoint;
import io.k2dv.garden.stats.dto.TopCustomerEntry;
import io.k2dv.garden.stats.dto.TopProductEntry;
import io.k2dv.garden.stats.service.StatsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@Tag(name = "Admin: Stats", description = "Dashboard analytics")
@RestController
@RequestMapping("/api/v1/admin/stats")
@RequiredArgsConstructor
public class AdminStatsController {

    private final StatsService statsService;

    @GetMapping
    @HasPermission("stats:read")
    public ApiResponse<StatsResponse> getStats(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.of(statsService.getStats(from, to));
    }

    @GetMapping("/time-series")
    @HasPermission("stats:read")
    public ApiResponse<List<TimeSeriesPoint>> getTimeSeries(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponse.of(statsService.getTimeSeries(from, to));
    }

    @GetMapping("/top-products")
    @HasPermission("stats:read")
    public ApiResponse<List<TopProductEntry>> getTopProducts(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.of(statsService.getTopProducts(from, to, Math.min(limit, 20)));
    }

    @GetMapping("/top-customers")
    @HasPermission("stats:read")
    public ApiResponse<List<TopCustomerEntry>> getTopCustomers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "5") int limit) {
        return ApiResponse.of(statsService.getTopCustomers(from, to, Math.min(limit, 20)));
    }
}
