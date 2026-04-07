package io.k2dv.garden.order.controller;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminOrderController.class)
@Import({ TestSecurityConfig.class, GlobalExceptionHandler.class })
class AdminOrderControllerTest {

  @Autowired
  MockMvc mvc;
  @MockitoBean
  OrderService orderService;

  private OrderResponse stubOrder(UUID id) {
    return new OrderResponse(id, UUID.randomUUID(), OrderStatus.PENDING_PAYMENT,
        new BigDecimal("99.98"), "usd", "cs_test_123", List.of(), null);
  }

  @Test
  void listOrders_returns200() throws Exception {
    PagedResult<OrderResponse> result = new PagedResult<>(
        List.of(stubOrder(UUID.randomUUID())),
        PageMeta.builder().page(0).pageSize(20).total(1L).build());
    when(orderService.list(any(), any())).thenReturn(result);

    mvc.perform(get("/api/v1/admin/orders"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.content").isArray());
  }

  @Test
  void getOrder_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(orderService.getOrderResponse(id)).thenReturn(stubOrder(id));

    mvc.perform(get("/api/v1/admin/orders/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.id").value(id.toString()));
  }

  @Test
  void getOrder_notFound_returns404() throws Exception {
    when(orderService.getOrderResponse(any()))
        .thenThrow(new NotFoundException("ORDER_NOT_FOUND", "Not found"));

    mvc.perform(get("/api/v1/admin/orders/{id}", UUID.randomUUID()))
        .andExpect(status().isNotFound());
  }

  @Test
  void cancelOrder_returns200() throws Exception {
    UUID id = UUID.randomUUID();
    when(orderService.cancelAndReturn(id)).thenReturn(stubOrder(id));

    mvc.perform(put("/api/v1/admin/orders/{id}/cancel", id))
        .andExpect(status().isOk());
  }

  @Test
  void cancelOrder_paidOrder_returns409() throws Exception {
    doThrow(new ConflictException("INVALID_ORDER_STATUS", "Cannot cancel paid order"))
        .when(orderService).cancelAndReturn(any());

    mvc.perform(put("/api/v1/admin/orders/{id}/cancel", UUID.randomUUID()))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("INVALID_ORDER_STATUS"));
  }

  @Test
  void cancelOrder_notFound_returns404() throws Exception {
    doThrow(new NotFoundException("ORDER_NOT_FOUND", "Not found"))
        .when(orderService).cancelAndReturn(any());

    mvc.perform(put("/api/v1/admin/orders/{id}/cancel", UUID.randomUUID()))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("ORDER_NOT_FOUND"));
  }
}
