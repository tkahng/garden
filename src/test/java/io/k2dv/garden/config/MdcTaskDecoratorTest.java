package io.k2dv.garden.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class MdcTaskDecoratorTest {

    private final MdcTaskDecorator decorator = new MdcTaskDecorator();

    @AfterEach
    void cleanup() {
        MDC.clear();
    }

    @Test
    void propagatesMdcContextToDecoratedTask() {
        MDC.put("requestId", "req-abc");
        MDC.put("userId", "user-123");

        AtomicReference<String> capturedRequestId = new AtomicReference<>();
        AtomicReference<String> capturedUserId = new AtomicReference<>();

        Runnable task = decorator.decorate(() -> {
            capturedRequestId.set(MDC.get("requestId"));
            capturedUserId.set(MDC.get("userId"));
        });
        // Clear caller MDC before running (simulates async thread having no prior context)
        MDC.clear();
        task.run();

        assertThat(capturedRequestId.get()).isEqualTo("req-abc");
        assertThat(capturedUserId.get()).isEqualTo("user-123");
    }

    @Test
    void clearsMdcAfterTaskCompletes() {
        MDC.put("requestId", "req-xyz");

        Runnable task = decorator.decorate(() -> {});
        MDC.clear();
        task.run();

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void clearsMdcAfterTaskThrows() {
        MDC.put("requestId", "req-throw");

        Runnable task = decorator.decorate(() -> { throw new RuntimeException("fail"); });
        MDC.clear();
        try { task.run(); } catch (RuntimeException ignored) {}

        assertThat(MDC.getCopyOfContextMap()).isNullOrEmpty();
    }

    @Test
    void worksWhenCallerMdcIsEmpty() {
        MDC.clear();
        AtomicReference<String> captured = new AtomicReference<>("sentinel");

        Runnable task = decorator.decorate(() -> captured.set(MDC.get("requestId")));
        task.run();

        assertThat(captured.get()).isNull();
    }
}
