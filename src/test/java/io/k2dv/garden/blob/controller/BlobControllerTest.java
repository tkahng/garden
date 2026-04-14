package io.k2dv.garden.blob.controller;

import io.k2dv.garden.blob.dto.BlobResponse;
import io.k2dv.garden.blob.dto.BlobUsageResponse;
import io.k2dv.garden.blob.service.BlobService;
import io.k2dv.garden.config.TestSecurityConfig;
import io.k2dv.garden.shared.exception.GlobalExceptionHandler;
import io.k2dv.garden.shared.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BlobController.class)
@Import({TestSecurityConfig.class, GlobalExceptionHandler.class})
class BlobControllerTest {

    @Autowired
    MockMvc mvc;

    @MockitoBean
    BlobService blobService;

    private BlobResponse sampleBlob(UUID id) {
        return new BlobResponse(id, "uploads/abc-test.jpg", "test.jpg", "image/jpeg", 4L,
            "http://localhost:9000/test/uploads/abc-test.jpg",
            "A test image", "Test Title", 800, 600, Instant.now());
    }

    @Test
    void upload_validFile_returns201() throws Exception {
        UUID id = UUID.randomUUID();
        when(blobService.upload(any())).thenReturn(sampleBlob(id));

        mvc.perform(multipart("/api/v1/admin/blobs")
                .file(new MockMultipartFile("file", "test.jpg", "image/jpeg", "data".getBytes())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.data.filename").value("test.jpg"))
            .andExpect(jsonPath("$.data.key").value("uploads/abc-test.jpg"))
            .andExpect(jsonPath("$.data.url").exists())
            .andExpect(jsonPath("$.data.width").value(800))
            .andExpect(jsonPath("$.data.height").value(600));
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
    void update_metadata_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(blobService.updateMetadata(eq(id), any())).thenReturn(sampleBlob(id));

        mvc.perform(patch("/api/v1/admin/blobs/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"alt\":\"A test image\",\"title\":\"Test Title\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.alt").value("A test image"))
            .andExpect(jsonPath("$.data.title").value("Test Title"));
    }

    @Test
    void delete_returns204() throws Exception {
        doNothing().when(blobService).delete(any());

        mvc.perform(delete("/api/v1/admin/blobs/{id}", UUID.randomUUID()))
            .andExpect(status().isNoContent());
    }

    @Test
    void bulkDelete_returns204() throws Exception {
        doNothing().when(blobService).bulkDelete(any());

        mvc.perform(delete("/api/v1/admin/blobs")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[\"" + UUID.randomUUID() + "\",\"" + UUID.randomUUID() + "\"]}"))
            .andExpect(status().isNoContent());
    }

    @Test
    void getUsages_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(blobService.getUsages(id)).thenReturn(List.of(new BlobUsageResponse("product", productId)));

        mvc.perform(get("/api/v1/admin/blobs/{id}/usages", id))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].entityType").value("product"))
            .andExpect(jsonPath("$.data[0].entityId").value(productId.toString()));
    }
}
