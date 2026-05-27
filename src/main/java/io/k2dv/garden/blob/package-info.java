/**
 * Blob/storage module for managing uploaded binary assets such as product images, article images,
 * and downloadable PDFs. Files are stored in an S3-compatible backend (configured via
 * {@code StorageProperties}) and tracked in the {@code BlobObject} database table. The module
 * provides upload validation, image dimension extraction, folder organisation, usage tracking,
 * and signed public URL resolution.
 */
package io.k2dv.garden.blob;
