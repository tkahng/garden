package io.k2dv.garden.blob.service;

import java.io.InputStream;

public interface StorageService {

    /**
     * Store an object in the given bucket at the given key.
     * contentLength must be provided — MinIO does not support chunked uploads.
     */
    void store(String bucket, String key, String contentType, InputStream data, long contentLength);

    /** Store in the default public bucket. */
    void store(String key, String contentType, InputStream data, long contentLength);

    /** Delete the object at the given key from the given bucket. Idempotent. */
    void delete(String bucket, String key);

    /** Delete from the default public bucket. Idempotent. */
    void delete(String key);

    /** Fetch the object from the given bucket as an InputStream. Caller must close the stream. */
    InputStream fetch(String bucket, String key);

    /** Fetch from the default public bucket. Caller must close the stream. */
    InputStream fetch(String key);

    /** Compute the public URL for a key in the given bucket. */
    String resolveUrl(String bucket, String key);

    /** Compute the public URL for a key in the default public bucket. */
    String resolveUrl(String key);
}
