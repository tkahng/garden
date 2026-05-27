/**
 * Spring Data JPA repositories for the product catalog domain. All product and variant queries
 * that touch live data must include {@code deletedAtIsNull} predicates to honour the soft-delete
 * contract. Specification-based queries are composed in {@code product.specification}.
 */
package io.k2dv.garden.product.repository;
