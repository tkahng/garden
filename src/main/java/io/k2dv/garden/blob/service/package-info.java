/**
 * Service layer for the blob/storage domain. {@code BlobService} is the application-level
 * orchestrator for file uploads, metadata management, and lifecycle operations. {@code StorageService}
 * is the low-level storage abstraction; {@code S3StorageService} is its AWS S3 / MinIO
 * implementation that delegates to the AWS SDK v2 {@code S3Client}.
 */
package io.k2dv.garden.blob.service;
