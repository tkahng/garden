package io.k2dv.garden.order.service;

import io.k2dv.garden.order.dto.OrderEventResponse;
import io.k2dv.garden.order.model.OrderEvent;
import io.k2dv.garden.order.model.OrderEventType;
import io.k2dv.garden.order.repository.OrderEventRepository;
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
        return OrderEventResponse.from(eventRepo.save(event));
    }

    @Transactional(readOnly = true)
    public List<OrderEventResponse> list(UUID orderId) {
        return eventRepo.findByOrderIdOrderByCreatedAtAsc(orderId)
            .stream().map(OrderEventResponse::from).toList();
    }
}
