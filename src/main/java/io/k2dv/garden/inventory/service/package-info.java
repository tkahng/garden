/**
 * Service layer for the inventory domain. {@code InventoryService} is the central coordinator for
 * stock receipt, manual adjustments, reservations, and sale confirmation; it uses pessimistic
 * locking to prevent overselling across concurrent requests. {@code LocationService} manages the
 * warehouse and store locations that inventory levels are scoped to.
 */
package io.k2dv.garden.inventory.service;
