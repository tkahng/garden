/**
 * Service layer for the collection domain. {@code CollectionService} manages the collection
 * lifecycle, rule authoring, and product membership for both admin and storefront read paths.
 * {@code CollectionMembershipService} is the low-level engine that evaluates tag rules and keeps
 * AUTOMATED collection memberships consistent whenever products or rules change.
 */
package io.k2dv.garden.collection.service;
