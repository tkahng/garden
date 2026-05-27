/**
 * JPA entity classes for the inventory domain. {@code InventoryItem} links a product variant to
 * the inventory system; {@code InventoryLevel} holds the per-location stock counters
 * ({@code quantityOnHand} and {@code quantityCommitted}); {@code InventoryTransaction} is the
 * append-only audit ledger; and {@code Location} represents a physical or logical stock-holding site.
 */
package io.k2dv.garden.inventory.model;
