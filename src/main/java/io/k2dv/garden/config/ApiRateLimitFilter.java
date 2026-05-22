package io.k2dv.garden.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Cache;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.annotation.Order;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Per-IP rate limiter for all /api/v1/** endpoints (excluding /api/v1/auth/**,
 * which has its own JDBC-backed limiter).
 *
 * Limits: 300 requests / minute per IP (token bucket, 5 req/sec refill).
 * Buckets are evicted after 2 minutes of inactivity via a bounded Caffeine cache
 * (max 100k unique IPs).
 *
 * X-Forwarded-For is only trusted when the direct connection comes from a configured
 * trusted-proxy address (app.rate-limit.trusted-proxies, defaults to loopback only).
 */
@Component
@Order(2)
public class ApiRateLimitFilter implements Filter {

    private static final int CAPACITY = 300;
    private static final Duration REFILL_PERIOD = Duration.ofMinutes(1);

    private final Cache<String, Bucket> buckets = Caffeine.newBuilder()
        .maximumSize(100_000)
        .expireAfterAccess(Duration.ofMinutes(2))
        .build();

    private final Set<String> trustedProxies;

    public ApiRateLimitFilter(Environment env) {
        List<String> configured = Binder.get(env)
            .bind("app.rate-limit.trusted-proxies", String[].class)
            .map(List::of)
            .orElse(List.of());
        // Always include loopback so local/dev deployments work without explicit config
        this.trustedProxies = configured.isEmpty()
            ? Set.of("127.0.0.1", "::1", "0:0:0:0:0:0:0:1")
            : configured.stream().collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public void doFilter(ServletRequest req, ServletResponse resp, FilterChain chain)
            throws IOException, ServletException {
        if (!(req instanceof HttpServletRequest http)) {
            chain.doFilter(req, resp);
            return;
        }

        String path = http.getRequestURI();
        if (!path.startsWith("/api/v1/") || path.startsWith("/api/v1/auth/")) {
            chain.doFilter(req, resp);
            return;
        }

        String ip = resolveClientIp(http);
        Bucket bucket = buckets.get(ip, k -> newBucket());

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

    // Only trust X-Forwarded-For when the direct connection is from a known proxy.
    // Without this guard any client could spoof an arbitrary IP to bypass per-IP limits.
    private String resolveClientIp(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (trustedProxies.contains(remoteAddr)) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                return forwarded.split(",")[0].trim();
            }
        }
        return remoteAddr;
    }
}
