package io.k2dv.garden.webhook.service;

import io.k2dv.garden.auth.service.EmailService;
import io.k2dv.garden.shared.AbstractIntegrationTest;
import io.k2dv.garden.shared.exception.NotFoundException;
import io.k2dv.garden.webhook.dto.CreateWebhookEndpointRequest;
import io.k2dv.garden.webhook.dto.UpdateWebhookEndpointRequest;
import io.k2dv.garden.webhook.dto.WebhookEndpointResponse;
import io.k2dv.garden.webhook.model.WebhookDelivery;
import io.k2dv.garden.webhook.model.WebhookDeliveryStatus;
import io.k2dv.garden.webhook.model.WebhookEndpoint;
import io.k2dv.garden.webhook.model.WebhookEventType;
import io.k2dv.garden.webhook.repository.WebhookDeliveryRepository;
import io.k2dv.garden.webhook.repository.WebhookEndpointRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundWebhookServiceIT extends AbstractIntegrationTest {

    @Autowired
    OutboundWebhookService webhookService;
    @Autowired
    WebhookEndpointRepository endpointRepo;
    @Autowired
    WebhookDeliveryRepository deliveryRepo;
    @MockitoBean
    EmailService emailService;

    private CreateWebhookEndpointRequest createReq(String url, List<String> events) {
        return new CreateWebhookEndpointRequest(url, "secret123", "test endpoint", events);
    }

    private WebhookEndpoint savedEndpoint(String url, List<String> events, boolean active) {
        WebhookEndpoint e = new WebhookEndpoint();
        e.setUrl(url);
        e.setSecret("secret123");
        e.setEvents(events);
        e.setActive(active);
        return endpointRepo.save(e);
    }

    @Test
    void create_persistsEndpoint() {
        WebhookEndpointResponse resp = webhookService.create(
                createReq("https://example.com/hook", List.of("ORDER_PLACED")));

        assertThat(resp.id()).isNotNull();
        assertThat(resp.url()).isEqualTo("https://example.com/hook");
        assertThat(resp.events()).containsExactly("ORDER_PLACED");
        assertThat(resp.active()).isTrue();
    }

    @Test
    void list_returnsAllEndpoints() {
        webhookService.create(createReq("https://a.example.com/hook", List.of()));
        webhookService.create(createReq("https://b.example.com/hook", List.of()));

        List<WebhookEndpointResponse> all = webhookService.list();

        assertThat(all).hasSizeGreaterThanOrEqualTo(2);
        assertThat(all).anyMatch(r -> r.url().equals("https://a.example.com/hook"));
        assertThat(all).anyMatch(r -> r.url().equals("https://b.example.com/hook"));
    }

    @Test
    void getById_found() {
        WebhookEndpointResponse created = webhookService.create(
                createReq("https://example.com/hook", List.of()));

        WebhookEndpointResponse found = webhookService.getById(created.id());

        assertThat(found.id()).isEqualTo(created.id());
        assertThat(found.url()).isEqualTo("https://example.com/hook");
    }

    @Test
    void getById_notFound_throwsNotFoundException() {
        assertThatThrownBy(() -> webhookService.getById(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void update_modifiesNonNullFields() {
        WebhookEndpointResponse created = webhookService.create(
                createReq("https://example.com/hook", List.of("ORDER_PLACED")));

        WebhookEndpointResponse updated = webhookService.update(created.id(),
                new UpdateWebhookEndpointRequest("https://new.example.com/hook", null, null, null, null));

        assertThat(updated.url()).isEqualTo("https://new.example.com/hook");
        assertThat(updated.events()).containsExactly("ORDER_PLACED");
    }

    @Test
    void update_canDeactivateEndpoint() {
        WebhookEndpointResponse created = webhookService.create(
                createReq("https://example.com/hook", List.of()));

        WebhookEndpointResponse updated = webhookService.update(created.id(),
                new UpdateWebhookEndpointRequest(null, null, null, null, false));

        assertThat(updated.active()).isFalse();
    }

    @Test
    void delete_removesEndpoint() {
        WebhookEndpointResponse created = webhookService.create(
                createReq("https://example.com/hook", List.of()));

        webhookService.delete(created.id());

        assertThatThrownBy(() -> webhookService.getById(created.id()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void delete_notFound_throwsNotFoundException() {
        assertThatThrownBy(() -> webhookService.delete(UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void scheduleDelivery_createsDeliveryForSubscribedEndpoint() {
        WebhookEndpoint endpoint = savedEndpoint("https://example.com/hook",
                List.of("ORDER_PLACED"), true);

        webhookService.scheduleDelivery(WebhookEventType.ORDER_PLACED,
                Map.of("orderId", UUID.randomUUID().toString()));

        List<WebhookDelivery> deliveries = deliveryRepo
                .findByEndpointId(endpoint.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(deliveries).hasSize(1);
        assertThat(deliveries.get(0).getEventType()).isEqualTo(WebhookEventType.ORDER_PLACED);
        assertThat(deliveries.get(0).getStatus()).isEqualTo(WebhookDeliveryStatus.PENDING);
    }

    @Test
    void scheduleDelivery_skipsInactiveEndpoints() {
        WebhookEndpoint inactive = savedEndpoint("https://example.com/hook",
                List.of("ORDER_PLACED"), false);

        webhookService.scheduleDelivery(WebhookEventType.ORDER_PLACED,
                Map.of("orderId", UUID.randomUUID().toString()));

        List<WebhookDelivery> deliveries = deliveryRepo
                .findByEndpointId(inactive.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(deliveries).isEmpty();
    }

    @Test
    void scheduleDelivery_wildcardEndpointReceivesAllEvents() {
        WebhookEndpoint wildcard = savedEndpoint("https://example.com/hook",
                List.of(), true);

        webhookService.scheduleDelivery(WebhookEventType.ORDER_PLACED,
                Map.of("orderId", UUID.randomUUID().toString()));
        webhookService.scheduleDelivery(WebhookEventType.INVOICE_ISSUED,
                Map.of("invoiceId", UUID.randomUUID().toString()));

        List<WebhookDelivery> deliveries = deliveryRepo
                .findByEndpointId(wildcard.getId(), PageRequest.of(0, 10)).getContent();
        assertThat(deliveries).hasSize(2);
    }

    @Test
    void scheduleDelivery_excludesNonMatchingSubscriptions() {
        WebhookEndpoint orderEndpoint = savedEndpoint("https://a.example.com/hook",
                List.of("ORDER_PLACED"), true);
        WebhookEndpoint invoiceEndpoint = savedEndpoint("https://b.example.com/hook",
                List.of("INVOICE_ISSUED"), true);

        webhookService.scheduleDelivery(WebhookEventType.ORDER_PLACED,
                Map.of("orderId", UUID.randomUUID().toString()));

        assertThat(deliveryRepo.findByEndpointId(orderEndpoint.getId(),
                PageRequest.of(0, 10)).getContent()).hasSize(1);
        assertThat(deliveryRepo.findByEndpointId(invoiceEndpoint.getId(),
                PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void scheduleDelivery_storesPayload() {
        UUID orderId = UUID.randomUUID();
        WebhookEndpoint endpoint = savedEndpoint("https://example.com/hook",
                List.of("ORDER_PLACED"), true);

        webhookService.scheduleDelivery(WebhookEventType.ORDER_PLACED,
                Map.of("orderId", orderId.toString(), "total", "99.99"));

        WebhookDelivery delivery = deliveryRepo
                .findByEndpointId(endpoint.getId(), PageRequest.of(0, 10))
                .getContent().get(0);
        assertThat(delivery.getPayload()).containsEntry("orderId", orderId.toString());
        assertThat(delivery.getPayload()).containsEntry("total", "99.99");
    }

    @Test
    void listDeliveries_returnsPaginatedResult() {
        WebhookEndpointResponse endpoint = webhookService.create(
                createReq("https://example.com/hook", List.of("ORDER_PLACED")));

        webhookService.scheduleDelivery(WebhookEventType.ORDER_PLACED,
                Map.of("orderId", UUID.randomUUID().toString()));
        webhookService.scheduleDelivery(WebhookEventType.ORDER_PLACED,
                Map.of("orderId", UUID.randomUUID().toString()));

        var result = webhookService.listDeliveries(endpoint.id(), PageRequest.of(0, 10));

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getMeta().getTotal()).isEqualTo(2);
    }

    @Test
    void listDeliveries_notFoundEndpoint_throwsNotFoundException() {
        assertThatThrownBy(() -> webhookService.listDeliveries(UUID.randomUUID(),
                PageRequest.of(0, 10)))
                .isInstanceOf(NotFoundException.class);
    }
}
