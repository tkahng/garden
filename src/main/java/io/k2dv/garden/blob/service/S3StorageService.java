package io.k2dv.garden.blob.service;

import io.k2dv.garden.blob.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    public void store(String key, String contentType, InputStream data, long contentLength) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(key)
            .contentType(contentType)
            .contentLength(contentLength)
            .build();
        s3Client.putObject(request, RequestBody.fromInputStream(data, contentLength));
    }

    @Override
    public void delete(String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(properties.getBucket())
            .key(key)
            .build();
        s3Client.deleteObject(request);
    }

    @Override
    public String resolveUrl(String key) {
        return properties.getBaseUrl() + "/" + key;
    }
}
