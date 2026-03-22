package io.k2dv.garden.shared.exception;

import io.k2dv.garden.config.TestSecurityConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.TestController.class)
@Import({GlobalExceptionHandler.class, TestSecurityConfig.class, GlobalExceptionHandlerTest.TestController.class})
class GlobalExceptionHandlerTest {

    @Autowired
    MockMvc mvc;

    @RestController
    static class TestController {
        @GetMapping("/test/not-found")
        String notFound() {
            throw new NotFoundException("PRODUCT_NOT_FOUND", "No product found");
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw new ConflictException("EMAIL_TAKEN", "Email already in use");
        }
    }

    @Test
    void notFound_returns404WithErrorEnvelope() throws Exception {
        mvc.perform(get("/test/not-found").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("PRODUCT_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("No product found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void conflict_returns409WithErrorEnvelope() throws Exception {
        mvc.perform(get("/test/conflict").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("EMAIL_TAKEN"))
                .andExpect(jsonPath("$.status").value(409));
    }
}
