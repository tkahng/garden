/**
 * JPA entities for the order module. {@code Order} is the aggregate root tracking lifecycle
 * status, totals, and payment references; {@code OrderItem} represents individual line items;
 * {@code OrderEvent} provides the immutable audit timeline for each order.
 */
package io.k2dv.garden.order.model;
