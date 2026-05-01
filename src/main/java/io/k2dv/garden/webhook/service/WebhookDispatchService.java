package io.k2dv.garden.webhook.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.k2dv.garden.webhook.model.WebhookDelivery;
import io.k2dv.garden.webhook.model.WebhookDeliveryStatus;
import io.k2dv.garden.webhook.model.WebhookEndpoint;
import io.k2dv.garden.webhook.repository.WebhookDeliveryRepository;
import io.k2dv.garden.webhook.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookDispatchService {

    private static final int MAX_ATTEMPTS = 5;
    private static final long[] RETRY_SECONDS = {60, 300, 1800, 7200, 86400};

    private final WebhookDeliveryRepository deliveryRepo;
    private final WebhookEndpointRepository endpointRepo;

    private final ObjectMapper objectMapper = new ObjectMapper()
        .registerModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build();

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    public void dispatchPending() {
        List<WebhookDelivery> deliveries = deliveryRepo.findDispatchable(Instant.now());
        for (WebhookDelivery delivery : deliveries) {
            dispatch(delivery);
        }
    }

    private void dispatch(WebhookDelivery delivery) {
        WebhookEndpoint endpoint = endpointRepo.findById(delivery.getEndpointId()).orElse(null);
        if (endpoint == null || !endpoint.isActive()) {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setResponseBody("Endpoint not found or inactive");
            deliveryRepo.save(delivery);
            return;
        }

        String body;
        try {
            body = objectMapper.writeValueAsString(Map.of(
                "event", delivery.getEventType().name(),
                "timestamp", Instant.now().toString(),
                "data", delivery.getPayload()
            ));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize webhook payload for delivery {}", delivery.getId(), e);
            return;
        }

        delivery.setAttemptCount(delivery.getAttemptCount() + 1);
        delivery.setLastAttemptedAt(Instant.now());

        try {
            String signature = sign(body, endpoint.getSecret());
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpoint.getUrl()))
                .timeout(Duration.ofSeconds(15))
                .header("Content-Type", "application/json")
                .header("X-Garden-Signature", "sha256=" + signature)
                .header("X-Garden-Event", delivery.getEventType().name())
                .header("X-Garden-Delivery", delivery.getId().toString())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            delivery.setHttpStatus(response.statusCode());
            delivery.setResponseBody(truncate(response.body(), 2000));

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                delivery.setStatus(WebhookDeliveryStatus.SUCCESS);
            } else {
                scheduleRetryOrFail(delivery);
            }
        } catch (Exception e) {
            log.warn("Webhook dispatch failed for delivery {}: {}", delivery.getId(), e.getMessage());
            delivery.setResponseBody(e.getMessage());
            scheduleRetryOrFail(delivery);
        }

        deliveryRepo.save(delivery);
    }

    private void scheduleRetryOrFail(WebhookDelivery delivery) {
        int attempt = delivery.getAttemptCount();
        if (attempt < MAX_ATTEMPTS) {
            long delaySecs = RETRY_SECONDS[Math.min(attempt - 1, RETRY_SECONDS.length - 1)];
            delivery.setNextRetryAt(Instant.now().plusSeconds(delaySecs));
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
        } else {
            delivery.setStatus(WebhookDeliveryStatus.FAILED);
            delivery.setNextRetryAt(null);
        }
    }

    private String sign(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] bytes = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(bytes);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return null;
        return s.length() > maxLen ? s.substring(0, maxLen) : s;
    }
}
