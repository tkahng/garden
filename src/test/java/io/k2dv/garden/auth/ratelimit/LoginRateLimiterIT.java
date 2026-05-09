package io.k2dv.garden.auth.ratelimit;

import io.k2dv.garden.shared.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LoginRateLimiterIT extends AbstractIntegrationTest {

    @Autowired LoginRateLimiter rateLimiter;
    @Autowired JdbcTemplate jdbc;

    private MockHttpServletRequest req(String ip) {
        var r = new MockHttpServletRequest();
        r.setRemoteAddr(ip);
        return r;
    }

    @Test
    void allowsRequestsUnderLimit() throws Exception {
        var req = req("10.0.0.1");
        for (int i = 0; i < 10; i++) {
            var resp = new MockHttpServletResponse();
            assertThat(rateLimiter.preHandle(req, resp, null)).isTrue();
        }
    }

    @Test
    void blocksRequestsOverLimit() throws Exception {
        var req = req("10.0.0.2");
        for (int i = 0; i < 10; i++) {
            rateLimiter.preHandle(req, new MockHttpServletResponse(), null);
        }
        var resp = new MockHttpServletResponse();
        assertThat(rateLimiter.preHandle(req, resp, null)).isFalse();
        assertThat(resp.getStatus()).isEqualTo(429);
    }

    @Test
    void differentIpsHaveIndependentLimits() throws Exception {
        var req1 = req("192.168.0.1");
        var req2 = req("192.168.0.2");
        for (int i = 0; i < 10; i++) {
            rateLimiter.preHandle(req1, new MockHttpServletResponse(), null);
        }
        // req2 should not be blocked
        var resp2 = new MockHttpServletResponse();
        assertThat(rateLimiter.preHandle(req2, resp2, null)).isTrue();
    }

    @Test
    void persitsAttemptsInDatabase() throws Exception {
        var req = req("10.1.1.1");
        rateLimiter.preHandle(req, new MockHttpServletResponse(), null);
        Long count = jdbc.queryForObject(
            "SELECT COUNT(*) FROM auth.rate_limit_attempts WHERE ip = '10.1.1.1'", Long.class);
        assertThat(count).isEqualTo(1L);
    }
}
