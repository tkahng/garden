package io.k2dv.garden.auth.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.time.Instant;

@Component
public class LoginRateLimiter implements HandlerInterceptor {

    // 10 attempts per 15-minute sliding window per IP
    private static final int MAX_ATTEMPTS = 10;
    private static final int WINDOW_MINUTES = 15;

    @Nullable
    private final JdbcTemplate jdbc;

    public LoginRateLimiter(@Nullable @Autowired(required = false) JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (jdbc == null) return true; // no data source available (slice tests)

        String ip = resolveClientIp(request);
        Instant windowStart = Instant.now().minusSeconds((long) WINDOW_MINUTES * 60);
        jdbc.update("DELETE FROM auth.rate_limit_attempts WHERE attempted_at < ?",
            java.sql.Timestamp.from(windowStart));
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.rate_limit_attempts WHERE ip = ? AND attempted_at >= ?",
            Long.class, ip, java.sql.Timestamp.from(windowStart));

        if (count != null && count >= MAX_ATTEMPTS) {
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getWriter().write("""
                {"error":"TOO_MANY_REQUESTS","message":"Too many login attempts. Try again later.","status":429}""");
            return false;
        }

        jdbc.update("INSERT INTO auth.rate_limit_attempts (ip) VALUES (?)", ip);
        return true;
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
