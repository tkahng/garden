/**
 * Inventory module for tracking stock levels per variant and warehouse location. Implements a
 * two-phase reservation model: stock is committed (reserved) on order placement, deducted from
 * on-hand when payment is captured, and released back to available when an order is cancelled.
 * Locations can be physical warehouses or logical fulfilment nodes; stock movements are recorded
 * as immutable {@code InventoryTransaction} entries for audit purposes.
 */
package io.k2dv.garden.inventory;
