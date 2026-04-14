package io.k2dv.garden.blob.dto;

public record BlobFilter(
    String contentType,
    String filenameContains
) {}
