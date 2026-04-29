package io.k2dv.garden.webhook.service;

import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.webhook.dto.CreateWebhookEndpointRequest;
import io.k2dv.garden.webhook.dto.UpdateWebhookEndpointRequest;
import io.k2dv.garden.webhook.dto.WebhookDeliveryResponse;
import io.k2dv.garden.webhook.dto.WebhookEndpointResponse;
import io.k2dv.garden.webhook.model.WebhookDelivery;
import io.k2dv.garden.webhook.model.WebhookEndpoint;
import io.k2dv.garden.webhook.model.WebhookEventType;
import io.k2dv.garden.webhook.repository.WebhookDeliveryRepository;
import io.k2dv.garden.webhook.repository.WebhookEndpointRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OutboundWebhookService {

    private final WebhookEndpointRepository endpointRepo;
    private final WebhookDeliveryRepository deliveryRepo;

    @Transactional
    public WebhookEndpointResponse create(CreateWebhookEndpointRequest req) {
        WebhookEndpoint e = new WebhookEndpoint();
        e.setUrl(req.url());
        e.setSecret(req.secret());
        e.setDescription(req.description());
        e.setEvents(req.events());
        return WebhookEndpointResponse.from(endpointRepo.save(e));
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpointResponse> list() {
        return endpointRepo.findAll().stream().map(WebhookEndpointResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public WebhookEndpointResponse getById(UUID id) {
        return WebhookEndpointResponse.from(require(id));
    }

    @Transactional
    public WebhookEndpointResponse update(UUID id, UpdateWebhookEndpointRequest req) {
        WebhookEndpoint e = require(id);
        if (req.url() != null) e.setUrl(req.url());
        if (req.secret() != null) e.setSecret(req.secret());
        if (req.description() != null) e.setDescription(req.description());
        if (req.events() != null) e.setEvents(req.events());
        if (req.active() != null) e.setActive(req.active());
        return WebhookEndpointResponse.from(endpointRepo.save(e));
    }

    @Transactional
    public void delete(UUID id) {
        require(id);
        endpointRepo.deleteById(id);
    }

    @Transactional(readOnly = true)
    public PagedResult<WebhookDeliveryResponse> listDeliveries(UUID endpointId, Pageable pageable) {
        require(endpointId);
        return PagedResult.of(
            deliveryRepo.findByEndpointId(endpointId, pageable),
            WebhookDeliveryResponse::from
        );
    }

    /**
     * Schedules delivery records for all active endpoints subscribed to this event.
     * Called by domain services after committing business state changes.
     */
    @Transactional
    public void scheduleDelivery(WebhookEventType eventType, Map<String, Object> payload) {
        List<WebhookEndpoint> targets = endpointRepo.findActiveByEvent(eventType.name());
        for (WebhookEndpoint endpoint : targets) {
            WebhookDelivery delivery = new WebhookDelivery();
            delivery.setEndpointId(endpoint.getId());
            delivery.setEventType(eventType);
            delivery.setPayload(payload);
            deliveryRepo.save(delivery);
        }
    }

    private WebhookEndpoint require(UUID id) {
        return endpointRepo.findById(id)
            .orElseThrow(() -> new NotFoundException("WEBHOOK_ENDPOINT_NOT_FOUND", "Webhook endpoint not found"));
    }
}
