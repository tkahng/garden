/**
 * Spring application events fired by {@code OrderService} to trigger transactional outbox emails.
 * {@code OrderConfirmedEvent} is published on successful payment; {@code OrderCancelledEvent} is
 * published when an order is cancelled, both subject to the customer's notification preferences.
 */
package io.k2dv.garden.order.event;
