/**
 * Service layer for the fulfillment module. {@code FulfillmentService} enforces the shipment
 * status machine, prevents over-fulfillment, recalculates order status after each change, and
 * dispatches customer shipping and delivery notifications via email and outbound webhooks.
 */
package io.k2dv.garden.fulfillment.service;
