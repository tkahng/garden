package io.k2dv.garden.blob.dto;

public record BlobStatsResponse(
    long totalFiles,
    long totalBytes
) {}
