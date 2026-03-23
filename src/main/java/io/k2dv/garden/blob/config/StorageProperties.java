package io.k2dv.garden.blob.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@ConfigurationProperties(prefix = "storage")
@Validated
@Getter
@Setter
public class StorageProperties {

    @NotBlank
    private String endpoint;

    @NotBlank
    private String bucket;

    @NotBlank
    private String accessKey;

    @NotBlank
    private String secretKey;

    @NotBlank
    private String baseUrl;

    private long maxUploadSize = 10_485_760L; // 10 MB default
}
