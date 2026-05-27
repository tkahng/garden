/**
 * Product catalog module responsible for the full lifecycle of products, variants, options, and
 * images. Products follow a soft-delete pattern (stamped with {@code deletedAt}) and progress
 * through DRAFT, ACTIVE, and ARCHIVED statuses. Variants hold the purchasable SKUs with price,
 * weight, and fulfillment settings; option axes (e.g., Size, Color) define the variant matrix.
 */
package io.k2dv.garden.product;
