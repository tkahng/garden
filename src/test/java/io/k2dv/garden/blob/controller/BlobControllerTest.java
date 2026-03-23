package io.k2dv.garden.blob.controller;

import io.k2dv.garden.blob.dto.BlobResponse;
import io.k2dv.garden.blob.service.BlobService;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BlobController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class BlobControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BlobService blobService;

    @Test
    void upload_validFile_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        var resp = new BlobResponse(id, "uploads/abc-test.jpg", "test.jpg", "image/jpeg", 4L,
            "http://localhost:9000/test/uploads/abc-test.jpg");
        when(blobService.upload(any())).thenReturn(resp);

        mvc.perform(multipart("/api/v1/admin/blobs")
                .file(new MockMultipartFile("file", "test.jpg", "image/jpeg", "data".getBytes())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.filename").value("test.jpg"))
            .andExpect(jsonPath("$.data.key").value("uploads/abc-test.jpg"))
            .andExpect(jsonPath("$.data.url").exists());
    }

    @Test
    void upload_oversizedFile_returns400() throws Exception {
        when(blobService.upload(any()))
            .thenThrow(new ValidationException("FILE_TOO_LARGE", "File exceeds maximum upload size"));

        mvc.perform(multipart("/api/v1/admin/blobs")
                .file(new MockMultipartFile("file", "big.jpg", "image/jpeg", "data".getBytes())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("FILE_TOO_LARGE"));
    }

    @Test
    void delete_returns204() throws Exception {
        doNothing().when(blobService).delete(any());

        mvc.perform(delete("/api/v1/admin/blobs/{id}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }
}
