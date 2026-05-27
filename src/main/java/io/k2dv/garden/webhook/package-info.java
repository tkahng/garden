/**
 * Outbound webhook delivery module that notifies subscriber endpoints about platform events
 * such as order placed, payment confirmed, and fulfilment updates.
 * Endpoint registration is managed separately from payload dispatch, which runs on a
 * scheduled poller with exponential retry back-off.
 */
package io.k2dv.garden.webhook;
