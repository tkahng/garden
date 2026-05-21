package io.k2dv.garden.config;

import org.slf4j.MDC;
import org.springframework.core.task.TaskDecorator;

import java.util.Map;

/**
 * Copies the MDC context from the submitting thread to the async worker thread so that
 * requestId, userId, and other diagnostic fields are available in @Async and executor logs.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable task) {
        Map<String, String> ctx = MDC.getCopyOfContextMap();
        return () -> {
            try {
                if (ctx != null) MDC.setContextMap(ctx);
                task.run();
            } finally {
                MDC.clear();
            }
        };
    }
}
