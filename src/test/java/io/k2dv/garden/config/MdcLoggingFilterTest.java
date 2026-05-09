package io.k2dv.garden.config;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MdcLoggingFilterTest {

    private final MdcLoggingFilter filter = new MdcLoggingFilter();

    @Test
    void setsRequestIdDuringRequest() throws Exception {
        AtomicReference<String> captured = new AtomicReference<>();
        FilterChain chain = (req, resp) -> captured.set(MDC.get("requestId"));

        filter.doFilter(new MockHttpServletRequest("GET", "/api/v1/products"),
                        new MockHttpServletResponse(), chain);

        assertThat(captured.get()).isNotNull().matches("[0-9a-f-]{36}");
    }

    @Test
    void clearsMdcAfterRequest() throws Exception {
        MDC.put("leftover", "value");
        filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                        mock(FilterChain.class));
        assertThat(MDC.get("requestId")).isNull();
        assertThat(MDC.get("leftover")).isNull();
    }

    @Test
    void setsMethodAndPath() throws Exception {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path   = new AtomicReference<>();
        FilterChain chain = (req, resp) -> {
            method.set(MDC.get("method"));
            path.set(MDC.get("path"));
        };
        filter.doFilter(new MockHttpServletRequest("POST", "/api/v1/checkout"),
                        new MockHttpServletResponse(), chain);
        assertThat(method.get()).isEqualTo("POST");
        assertThat(path.get()).isEqualTo("/api/v1/checkout");
    }
}
