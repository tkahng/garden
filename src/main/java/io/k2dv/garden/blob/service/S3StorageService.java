package io.k2dv.garden.blob.service;

import io.k2dv.garden.blob.config.StorageProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.io.InputStream;

@Service
@RequiredArgsConstructor
public class S3StorageService implements StorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;

    @Override
    public void store(String bucket, String key, String contentType, InputStream data, long contentLength) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .contentType(contentType)
            .contentLength(contentLength)
            .build();
        s3Client.putObject(request, RequestBody.fromInputStream(data, contentLength));
    }

    @Override
    public void store(String key, String contentType, InputStream data, long contentLength) {
        store(properties.getBucket(), key, contentType, data, contentLength);
    }

    @Override
    public InputStream fetch(String bucket, String key) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
        return s3Client.getObject(request);
    }

    @Override
    public InputStream fetch(String key) {
        return fetch(properties.getBucket(), key);
    }

    @Override
    public void delete(String bucket, String key) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
        s3Client.deleteObject(request);
    }

    @Override
    public void delete(String key) {
        delete(properties.getBucket(), key);
    }

    @Override
    public String resolveUrl(String bucket, String key) {
        return properties.getBaseUrl().replace("/" + properties.getBucket(), "/" + bucket) + "/" + key;
    }

    @Override
    public String resolveUrl(String key) {
        if (key.startsWith("http://") || key.startsWith("https://")) {
            return key;
        }
        return properties.getBaseUrl() + "/" + key;
    }
}
