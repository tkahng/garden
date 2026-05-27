/**
 * JPA entity classes for the product catalog domain. Key entities are {@code Product},
 * {@code ProductVariant} (holds SKU, price, and option-value links), {@code ProductOption} /
 * {@code ProductOptionValue} (define the variant matrix axes), and {@code ProductImage} (links
 * a product to a {@code BlobObject} for gallery display).
 */
package io.k2dv.garden.product.model;
