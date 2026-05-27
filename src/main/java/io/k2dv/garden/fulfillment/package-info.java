/**
 * Fulfillment module: tracks shipments and fulfillment status per order, supporting partial
 * fulfillment where items may ship in separate packages with independent carrier tracking numbers.
 * Automatically re-derives the parent order status after each fulfillment change.
 */
package io.k2dv.garden.fulfillment;
