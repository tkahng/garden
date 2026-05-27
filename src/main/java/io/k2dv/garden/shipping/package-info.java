/**
 * Shipping module: manages shipping zones (grouped by country/province) and their associated
 * rates (flat, weight-banded, or minimum-order-gated). At checkout, eligible rates are
 * resolved for the buyer's delivery address and order value; a chosen rate is re-validated
 * at order placement to guard against stale cart state.
 */
package io.k2dv.garden.shipping;
