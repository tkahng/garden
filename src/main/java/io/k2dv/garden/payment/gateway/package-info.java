/**
 * Stripe gateway abstraction: a thin wrapper around the Stripe Java SDK that isolates all direct
 * Stripe API calls behind an interface. The abstraction allows the gateway to be mocked in tests
 * without requiring live Stripe credentials or network access.
 */
package io.k2dv.garden.payment.gateway;
