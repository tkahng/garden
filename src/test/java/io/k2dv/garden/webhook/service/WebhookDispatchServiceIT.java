package io.k2dv.garden.webhook.service;

import com.sun.net.httpserver.HttpServer;
import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.webhook.model.WebhookDelivery;
import io.k2dv.garden.webhook.model.WebhookDeliveryStatus;
import io.k2dv.garden.webhook.model.WebhookEndpoint;
import io.k2dv.garden.webhook.model.WebhookEventType;
import io.k2dv.garden.webhook.repository.WebhookDeliveryRepository;
import io.k2dv.garden.webhook.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class WebhookDispatchServiceIT extends AbstractIntegrationTest {

    @Autowired
    WebhookDispatchService dispatchService;
    @Autowired
    WebhookEndpointRepository endpointRepo;
    @Autowired
    WebhookDeliveryRepository deliveryRepo;
    @MockitoBean
    EmailService emailService;

    private HttpServer server;
    private int serverPort;
    private final AtomicInteger responseCode = new AtomicInteger(200);

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/webhook", exchange -> {
            exchange.sendResponseHeaders(responseCode.get(), 0);
            exchange.close();
        });
        server.setExecutor(Executors.newSingleThreadExecutor());
        server.start();
        serverPort = server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    private WebhookEndpoint savedEndpoint(String url) {
        WebhookEndpoint e = new WebhookEndpoint();
        e.setUrl(url);
        e.setSecret("test-secret");
        e.setEvents(List.of("ORDER_PLACED"));
        return endpointRepo.save(e);
    }

    private WebhookDelivery savedDelivery(WebhookEndpoint endpoint) {
        WebhookDelivery d = new WebhookDelivery();
        d.setEndpointId(endpoint.getId());
        d.setEventType(WebhookEventType.ORDER_PLACED);
        d.setPayload(Map.of("orderId", "test-order-id"));
        return deliveryRepo.save(d);
    }

    @Test
    void dispatchPending_successResponse_marksDeliverySuccess() {
        responseCode.set(200);
        WebhookEndpoint endpoint = savedEndpoint("http://localhost:" + serverPort + "/webhook");
        WebhookDelivery delivery = savedDelivery(endpoint);

        dispatchService.dispatchPending();

        WebhookDelivery updated = deliveryRepo.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(WebhookDeliveryStatus.SUCCESS);
        assertThat(updated.getHttpStatus()).isEqualTo(200);
        assertThat(updated.getAttemptCount()).isEqualTo(1);
        assertThat(updated.getLastAttemptedAt()).isNotNull();
    }

    @Test
    void dispatchPending_errorResponse_schedulesRetry() {
        responseCode.set(500);
        WebhookEndpoint endpoint = savedEndpoint("http://localhost:" + serverPort + "/webhook");
        WebhookDelivery delivery = savedDelivery(endpoint);

        dispatchService.dispatchPending();

        WebhookDelivery updated = deliveryRepo.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(updated.getNextRetryAt()).isNotNull().isAfter(Instant.now().minusSeconds(1));
        assertThat(updated.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void dispatchPending_connectionRefused_schedulesRetry() {
        WebhookEndpoint endpoint = savedEndpoint("http://localhost:" + serverPort + "/webhook");
        WebhookDelivery delivery = savedDelivery(endpoint);
        server.stop(0);
        server = null;

        dispatchService.dispatchPending();

        WebhookDelivery updated = deliveryRepo.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(updated.getNextRetryAt()).isNotNull();
        assertThat(updated.getAttemptCount()).isEqualTo(1);
    }

    @Test
    void dispatchPending_inactiveEndpoint_marksDeliveryFailed() {
        WebhookEndpoint endpoint = savedEndpoint("http://localhost:" + serverPort + "/webhook");
        endpoint.setActive(false);
        endpointRepo.save(endpoint);
        WebhookDelivery delivery = savedDelivery(endpoint);

        dispatchService.dispatchPending();

        WebhookDelivery updated = deliveryRepo.findById(delivery.getId()).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(WebhookDeliveryStatus.FAILED);
        assertThat(updated.getResponseBody()).contains("inactive");
    }

    @Test
    void dispatchPending_sendsHmacSignatureHeader() {
        List<String> capturedSignatures = new CopyOnWriteArrayList<>();
        server.createContext("/signed", exchange -> {
            String sig = exchange.getRequestHeaders().getFirst("X-Garden-Signature");
            if (sig != null) capturedSignatures.add(sig);
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });

        WebhookEndpoint endpoint = savedEndpoint("http://localhost:" + serverPort + "/signed");
        savedDelivery(endpoint);

        dispatchService.dispatchPending();

        assertThat(capturedSignatures).isNotEmpty();
        assertThat(capturedSignatures.get(0)).startsWith("sha256=");
    }
}
