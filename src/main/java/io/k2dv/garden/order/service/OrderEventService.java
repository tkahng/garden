package io.k2dv.garden.order.service;

import io.k2dv.garden.order.dto.OrderEventResponse;
import io.k2dv.garden.order.model.OrderEvent;
import io.k2dv.garden.order.model.OrderEventType;
import io.k2dv.garden.order.repository.OrderEventRepository;
import io.k2dv.garden.webhook.model.WebhookEventType;
import io.k2dv.garden.webhook.service.OutboundWebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderEventService {

    private final OrderEventRepository eventRepo;
    private final OutboundWebhookService outboundWebhookService;

    private static final Map<OrderEventType, WebhookEventType> WEBHOOK_MAP = Map.of(
        OrderEventType.ORDER_PLACED, WebhookEventType.ORDER_PLACED,
        OrderEventType.PAYMENT_CONFIRMED, WebhookEventType.ORDER_PAID,
        OrderEventType.ORDER_CANCELLED, WebhookEventType.ORDER_CANCELLED,
        OrderEventType.ORDER_REFUNDED, WebhookEventType.ORDER_REFUNDED
    );

    @Transactional
    public OrderEventResponse emit(UUID orderId, OrderEventType type, String message,
                                   UUID authorId, String authorName, Map<String, Object> metadata) {
        OrderEvent event = new OrderEvent();
        event.setOrderId(orderId);
        event.setType(type);
        event.setMessage(message);
        event.setAuthorId(authorId);
        event.setAuthorName(authorName);
        event.setMetadata(metadata);
        OrderEventResponse response = OrderEventResponse.from(eventRepo.save(event));

        WebhookEventType webhookType = WEBHOOK_MAP.get(type);
        if (webhookType != null) {
            Map<String, Object> payload = metadata != null
                ? Map.of("orderId", orderId.toString(), "event", type.name(), "metadata", metadata)
                : Map.of("orderId", orderId.toString(), "event", type.name());
            outboundWebhookService.scheduleDelivery(webhookType, payload);
        }

        return response;
    }

    @Transactional(readOnly = true)
    public List<OrderEventResponse> list(UUID orderId) {
        return eventRepo.findByOrderIdOrderByCreatedAtAsc(orderId)
            .stream().map(OrderEventResponse::from).toList();
    }
}
