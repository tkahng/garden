package io.k2dv.garden.blob.dto;

import io.k2dv.garden.blob.model.BlobObject;

import java.time.Instant;
import java.util.UUID;

public record BlobResponse(
    UUID id,
    String key,
    String filename,
    String contentType,
    long size,
    String url,
    String alt,
    String title,
    Integer width,
    Integer height,
    Instant createdAt
) {
    public static BlobResponse from(BlobObject blob, String url) {
        return new BlobResponse(
            blob.getId(),
            blob.getKey(),
            blob.getFilename(),
            blob.getContentType(),
            blob.getSize(),
            url,
            blob.getAlt(),
            blob.getTitle(),
            blob.getWidth(),
            blob.getHeight(),
            blob.getCreatedAt());
    }
}
