package io.k2dv.garden.order.controller;

import io.k2dv.garden.config.TestCurrentUserConfig;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.order.dto.OrderResponse;
import io.k2dv.garden.order.model.OrderStatus;
import io.k2dv.garden.order.service.OrderService;
import io.k2dv.garden.shared.dto.PageMeta;
import io.k2dv.garden.shared.dto.PagedResult;
import io.k2dv.garden.shared.exception.ConflictException;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static io.k2dv.garden.config.TestCurrentUserConfig.STUB_USER_ID;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = StorefrontOrderController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class, TestCurrentUserConfig.class})
class StorefrontOrderControllerTest {

    @Autowired MockMvc mvc;
    @MockitoBean OrderService orderService;

    private OrderResponse stubOrder(UUID id, UUID userId, OrderStatus status) {
        return new OrderResponse(id, userId, status,
            new BigDecimal("99.98"), "usd", "cs_test_123", null, null, null, null, List.of(), null);
    }

    @Test
    void listOrders_returns200WithPagedContent() throws Exception {
        UUID orderId = UUID.randomUUID();
        PagedResult<OrderResponse> result = new PagedResult<>(
            List.of(stubOrder(orderId, STUB_USER_ID, OrderStatus.PAID)),
            PageMeta.builder().page(0).pageSize(20).total(1L).build());
        when(orderService.list(any(), any())).thenReturn(result);

        mvc.perform(get("/api/v1/storefront/orders"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.content[0].id").value(orderId.toString()));
    }

    @Test
    void getOrder_ownedOrder_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getOrderResponse(id)).thenReturn(stubOrder(id, STUB_USER_ID, OrderStatus.PAID));

        mvc.perform(get("/api/v1/storefront/orders/{id}", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(id.toString()));
    }

    @Test
    void getOrder_anotherUsersOrder_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getOrderResponse(id))
            .thenReturn(stubOrder(id, UUID.randomUUID(), OrderStatus.PAID));

        mvc.perform(get("/api/v1/storefront/orders/{id}", id))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("ORDER_NOT_OWNED"));
    }

    @Test
    void getOrder_notFound_returns404() throws Exception {
        when(orderService.getOrderResponse(any()))
            .thenThrow(new NotFoundException("ORDER_NOT_FOUND", "Not found"));

        mvc.perform(get("/api/v1/storefront/orders/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrder_ownedPendingOrder_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getOrderResponse(id))
            .thenReturn(stubOrder(id, STUB_USER_ID, OrderStatus.PENDING_PAYMENT));
        when(orderService.cancelAndReturn(id))
            .thenReturn(stubOrder(id, STUB_USER_ID, OrderStatus.CANCELLED));

        mvc.perform(put("/api/v1/storefront/orders/{id}/cancel", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    void cancelOrder_anotherUsersOrder_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getOrderResponse(id))
            .thenReturn(stubOrder(id, UUID.randomUUID(), OrderStatus.PENDING_PAYMENT));

        mvc.perform(put("/api/v1/storefront/orders/{id}/cancel", id))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("ORDER_NOT_OWNED"));
    }

    @Test
    void cancelOrder_paidOrder_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.getOrderResponse(id))
            .thenReturn(stubOrder(id, STUB_USER_ID, OrderStatus.PAID));
        when(orderService.cancelAndReturn(id))
            .thenThrow(new ConflictException("INVALID_ORDER_STATUS", "Cannot cancel paid order"));

        mvc.perform(put("/api/v1/storefront/orders/{id}/cancel", id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("INVALID_ORDER_STATUS"));
    }

    @Test
    void refundOrder_paidOrder_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.refundOrder(eq(id), eq(STUB_USER_ID)))
            .thenReturn(stubOrder(id, STUB_USER_ID, OrderStatus.REFUNDED));

        mvc.perform(post("/api/v1/storefront/orders/{id}/refund", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("REFUNDED"));
    }

    @Test
    void refundOrder_nonPaidOrder_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.refundOrder(eq(id), eq(STUB_USER_ID)))
            .thenThrow(new ConflictException("INVALID_ORDER_STATUS", "Cannot refund order in status: PENDING_PAYMENT"));

        mvc.perform(post("/api/v1/storefront/orders/{id}/refund", id))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error").value("INVALID_ORDER_STATUS"));
    }

    @Test
    void refundOrder_anotherUsersOrder_returns400() throws Exception {
        UUID id = UUID.randomUUID();
        when(orderService.refundOrder(eq(id), eq(STUB_USER_ID)))
            .thenThrow(new io.k2dv.garden.shared.exception.ValidationException("ORDER_NOT_OWNED",
                "Order does not belong to current user"));

        mvc.perform(post("/api/v1/storefront/orders/{id}/refund", id))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("ORDER_NOT_OWNED"));
    }
}
