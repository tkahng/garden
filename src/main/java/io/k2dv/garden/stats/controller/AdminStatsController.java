package io.k2dv.garden.stats.controller;

import io.k2dv.garden.auth.security.HasPermission;
import io.k2dv.garden.shared.dto.ApiResponse;
import io.k2dv.garden.stats.dto.StatsResponse;
import io.k2dv.garden.stats.service.StatsService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

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
}
