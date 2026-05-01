package io.k2dv.garden.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.product.dto.*;
import io.k2dv.garden.product.model.ProductStatus;
import io.k2dv.garden.product.service.*;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = AdminProductController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class AdminProductControllerTest {

    @Autowired MockMvc mvc;
    private final ObjectMapper mapper = new ObjectMapper();

    @MockitoBean ProductService productService;
    @MockitoBean VariantService variantService;
    @MockitoBean OptionService optionService;
    @MockitoBean ProductImageService imageService;

    private AdminProductResponse stubProduct() {
        return new AdminProductResponse(UUID.randomUUID(), "T-Shirt", null, "t-shirt",
            null, null, ProductStatus.DRAFT, null, List.of(), List.of(), List.of(), List.of(), null, null, null, null, null, null);
    }

    @Test
    void createProduct_validRequest_returns201() throws Exception {
        when(productService.create(any())).thenReturn(stubProduct());

        mvc.perform(post("/api/v1/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(new CreateProductRequest("T-Shirt", null, null, null, null, List.of(), null, null))))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.title").value("T-Shirt"))
            .andExpect(jsonPath("$.data.status").value("DRAFT"));
    }

    @Test
    void createProduct_missingTitle_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/products")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"title\":\"\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void getProduct_notFound_returns404() throws Exception {
        when(productService.getAdmin(any()))
            .thenThrow(new NotFoundException("PRODUCT_NOT_FOUND", "Product not found"));

        mvc.perform(get("/api/v1/admin/products/{id}", UUID.randomUUID()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"));
    }

    @Test
    void changeStatus_returns200() throws Exception {
        when(productService.changeStatus(any(), any())).thenReturn(stubProduct());

        mvc.perform(patch("/api/v1/admin/products/{id}/status", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\":\"ACTIVE\"}"))
            .andExpect(status().isOk());
    }

    @Test
    void deleteProduct_returns204() throws Exception {
        doNothing().when(productService).softDelete(any());

        mvc.perform(delete("/api/v1/admin/products/{id}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void reorderImages_routingBeforeImageId_returns200() throws Exception {
        doNothing().when(imageService).reorderImages(any(), any());

        mvc.perform(patch("/api/v1/admin/products/{id}/images/positions", UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("[]"))
            .andExpect(status().isOk());
    }

    @Test
    void createOptionValue_returns201() throws Exception {
        var optionValueResponse = new io.k2dv.garden.product.dto.ProductOptionValueResponse(
            UUID.randomUUID(), "Red", 0);
        when(optionService.createOptionValue(any(), any(), any())).thenReturn(optionValueResponse);

        mvc.perform(post("/api/v1/admin/products/{id}/options/{optId}/values",
                    UUID.randomUUID(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Red\",\"position\":0}"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.label").value("Red"));
    }

    @Test
    void createOptionValue_missingLabel_returns400() throws Exception {
        mvc.perform(post("/api/v1/admin/products/{id}/options/{optId}/values",
                    UUID.randomUUID(), UUID.randomUUID())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"\",\"position\":0}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    void deleteOptionValue_returns204() throws Exception {
        doNothing().when(optionService).deleteOptionValue(any(), any(), any());

        mvc.perform(delete("/api/v1/admin/products/{id}/options/{optId}/values/{valId}",
                    UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }
}
