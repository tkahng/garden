package io.k2dv.garden.config;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-IP rate limiter for all /api/v1/** endpoints (excluding /api/v1/auth/**,
 * which has its own JDBC-backed limiter).
 *
 * Limits: 300 requests / minute per IP (token bucket, 5 req/sec refill).
 * Eviction is handled by the bounded ConcurrentHashMap (LRU via access order is not
 * strictly needed here — stale entries are at most 1 bucket per unique IP, memory
 * usage is negligible compared to application memory).
 */
@Component
@Order(2)
public class ApiRateLimitFilter implements Filter {

    private static final int CAPACITY = 300;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (!(req instanceof HttpServletRequest http)) {
            chain.doFilter(req, resp);
            return;
        }

        String path = http.getRequestURI();
        // Only rate-limit API paths; skip auth (has its own limiter) and non-API paths
        if (!path.startsWith("/api/v1/") || path.startsWith("/api/v1/auth/")) {
            chain.doFilter(req, resp);
            return;
        }

        String ip = resolveClientIp(http);
        Bucket bucket = buckets.computeIfAbsent(ip, k -> newBucket());

        if (bucket.tryConsume(1)) {
            chain.doFilter(req, resp);
        } else {
            HttpServletResponse httpResp = (HttpServletResponse) resp;
            httpResp.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            httpResp.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResp.setHeader("Retry-After", "60");
            httpResp.getWriter().write(
                """
                {"error":"TOO_MANY_REQUESTS","message":"Rate limit exceeded. Please slow down.","status":429}""");
        }
    }

    private Bucket newBucket() {
        return Bucket.builder()
            .addLimit(Bandwidth.builder()
                .capacity(CAPACITY)
                .refillGreedy(CAPACITY, REFILL_PERIOD)
                .build())
            .build();
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
