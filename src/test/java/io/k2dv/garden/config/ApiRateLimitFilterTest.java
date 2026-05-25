package io.k2dv.garden.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ApiRateLimitFilterTest {

    private ApiRateLimitFilter filter;

    // No trusted proxies configured → only loopback (127.0.0.1) is trusted
    private static ApiRateLimitFilter filterWithNoProxies() {
        return new ApiRateLimitFilter(new MockEnvironment());
    }

    // Filter that trusts the given proxy IP (e.g. a load-balancer address in tests)
    private static ApiRateLimitFilter filterWithTrustedProxy(String proxyIp) {
        MockEnvironment env = new MockEnvironment();
        env.setProperty("app.rate-limit.trusted-proxies[0]", proxyIp);
        return new ApiRateLimitFilter(env);
    }

    @BeforeEach
    void setUp() {
        filter = filterWithNoProxies();
    }

    @Test
    void allowsRequestUnderLimit() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/products");
        req.setRemoteAddr("10.0.0.1");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void returns429WhenLimitExceeded() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/products");
        req.setRemoteAddr("10.0.0.2");
        FilterChain chain = mock(FilterChain.class);

        // Exhaust the bucket (300 requests)
        for (int i = 0; i < 300; i++) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            filter.doFilter(req, resp, chain);
        }

        // 301st request should be rate limited
        MockHttpServletResponse blockedResp = new MockHttpServletResponse();
        filter.doFilter(req, blockedResp, chain);

        assertThat(blockedResp.getStatus()).isEqualTo(429);
        assertThat(blockedResp.getContentAsString()).contains("TOO_MANY_REQUESTS");
        assertThat(blockedResp.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void skipsRateLimitForAuthPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/v1/auth/login");
        req.setRemoteAddr("10.0.0.3");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void skipsRateLimitForNonApiPaths() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/actuator/health");
        req.setRemoteAddr("10.0.0.4");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
    }

    @Test
    void bucketsArePerIp() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Exhaust IP A
        MockHttpServletRequest reqA = new MockHttpServletRequest("GET", "/api/v1/products");
        reqA.setRemoteAddr("10.0.0.10");
        for (int i = 0; i < 300; i++) {
            filter.doFilter(reqA, new MockHttpServletResponse(), chain);
        }

        // IP B should still be allowed
        MockHttpServletRequest reqB = new MockHttpServletRequest("GET", "/api/v1/products");
        reqB.setRemoteAddr("10.0.0.11");
        MockHttpServletResponse respB = new MockHttpServletResponse();
        filter.doFilter(reqB, respB, chain);

        assertThat(respB.getStatus()).isEqualTo(200);
    }

    @Test
    void xForwardedForIgnoredFromUntrustedRemoteAddr() throws Exception {
        // Remote addr 10.0.0.99 is not in the trusted-proxy list — XFF should be ignored
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/products");
        req.addHeader("X-Forwarded-For", "1.2.3.4");
        req.setRemoteAddr("10.0.0.99");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }

    @Test
    void xForwardedForTrustedFromConfiguredProxy() throws Exception {
        // Configure 10.0.0.99 as a trusted proxy — XFF from it should be respected
        ApiRateLimitFilter proxyFilter = filterWithTrustedProxy("10.0.0.99");

        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/v1/products");
        req.addHeader("X-Forwarded-For", "203.0.113.1, 10.0.0.99");
        req.setRemoteAddr("10.0.0.99");
        MockHttpServletResponse resp = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        proxyFilter.doFilter(req, resp, chain);

        verify(chain).doFilter(req, resp);
        assertThat(resp.getStatus()).isEqualTo(200);
    }
}
